package com.ryu.blog.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 用户权限快照缓存（单级：进程内 Caffeine）
 *
 * <h3>两级读取路径</h3>
 * <pre>
 *   L1  本地 Caffeine（TTL 默认 60 秒）—— 鉴权路径只读它，零 IO
 *   L2  数据库                          —— 权威来源
 * </pre>
 *
 * <p><b>为什么只有一层</b>：原先这里还有一层 Redis 共享快照 + 一整套 epoch 版本号机制
 * （{@code bumpEpoch} / 比对过期 / 写失败自愈）。那一层是为「多实例部署下撤销权限要跨实例收敛」
 * 服务的，而本项目是<b>单实例部署</b>（{@code docker-compose.yml} 没有 replicas），
 * 那份能力用不上；代价却实打实：
 * <ul>
 *   <li>它不是「加速层」而是<b>前置条件</b> —— 快照「先写 Redis、成功了才写本地」，
 *       版本号也是「先自增成功才继续」。Redis 一抖就是<b>全站 403</b>；</li>
 *   <li>它带来了一整套心智负担：序列化契约、脏数据兜底、版本号自愈。</li>
 * </ul>
 * 现在去掉它：本地缓存写不进去的唯一原因是数据库读不到，此时本来就该 fail-closed。
 *
 * <p><b>为什么需要它</b>：Sa-Token 的 {@code StpInterface} 是<b>同步</b>接口
 * （{@code List<String> getPermissionList(Object loginId, String loginType)}），
 * 而本项目是全响应式栈。直接让 {@code StpInterface} 去查库，要么阻塞事件循环，
 * 要么在 R2DBC 上 {@code block()} —— 后者在 Reactor 里是死锁风险。
 *
 * <p>所以这里的分工是：
 * <ul>
 *   <li><b>写入永远是反应式的</b>：{@link #refresh(Long)} 从数据库解析权限，
 *       写入本进程内的快照；</li>
 *   <li><b>读取永远是纯内存的</b>：{@link #getSnapshot(Long)} 不碰 IO，
 *       所以 {@code StpInterface} 调用它没有任何阻塞代价。</li>
 * </ul>
 *
 * <p>预热时机见 {@code UserPermissionWarmupFilter}：它在所有 WebFilter 阶段、
 * Controller 注解鉴权<b>之前</b>把快照准备好。
 *
 * <p><b>一致性取舍</b>：快照只在本进程内，多实例部署时其它实例最多滞后一个 TTL。
 * 本实例内的角色/权限/用户角色变更会主动 {@link #refresh(Long)} 刷新，
 * 所以真正会漏掉的只有「没走 refresh 路径的变更」——那属于 bug，不是设计。
 *
 * <h3>三件必须守住的事</h3>
 * <ol>
 *   <li><b>内存有界</b>：用 Caffeine（{@code expireAfterWrite} + {@code maximumSize}），
 *       过期条目会被真正淘汰。原先是裸 {@code ConcurrentHashMap}，
 *       过期条目<b>永不清理</b> —— 内存随「历史上登录过的用户数」单调增长。</li>
 *   <li><b>不惊群</b>：同一个用户的并发刷新共用一次查库（single-flight）。
 *       否则 TTL 一到，N 个并发请求会同时打两次库。</li>
 *   <li><b>失败要退避</b>：刷新失败后进入一个短退避窗口，期间不再打库。
 *       否则数据库一抖动，每个后续请求都重试 —— 查询量被放大 N 倍，
 *       同时该用户被 fail-closed 成 403，现场看起来像「权限全没了」。</li>
 * </ol>
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserPermissionCache {

    /**
     * 快照条目数上限 —— 兜底，防止用户数异常增长时把内存吃光。
     */
    private static final long MAX_ENTRIES = 10_000L;

    private final UserPermissionResolver resolver;

    /**
     * 快照本体。{@code expireAfterWrite} 让过期条目被真正淘汰（内存有界），
     * {@code getIfPresent} 是纯内存读，不阻塞。
     */
    private final Cache<Long, PermissionSnapshot> snapshots;

    /**
     * 刷新失败的时间戳。也在 Caffeine 里 —— 它同样会随退避窗口过期，
     * 不能变成第二张无界的表。
     */
    private final Cache<Long, Long> recentFailures;

    /**
     * 正在进行的<b>预热</b>（single-flight，读路径）。
     */
    private final Map<Long, Mono<Void>> warmFlights = new ConcurrentHashMap<>();

    /**
     * 正在进行的<b>强制刷新</b>（single-flight，写路径）。
     *
     * <p><b>必须与预热分开</b>：共用一张表的话，管理员发起的撤销可能被一个
     * 「读路径的预热」顶掉 —— computeIfAbsent 命中已有条目就直接返回它，
     * 于是<b>新快照不会写入</b>，撤销被整条吞掉，而调用方照样拿到 200。
     */
    private final Map<Long, Mono<Void>> refreshFlights = new ConcurrentHashMap<>();

    public UserPermissionCache(
            UserPermissionResolver resolver,
            @Value("${blog.auth.permission-snapshot-local-ttl-ms:60000}") long localTtlMillis,
            @Value("${blog.auth.permission-snapshot-backoff-ms:5000}") long failureBackoffMillis) {
        this.resolver = resolver;
        this.snapshots = Caffeine.newBuilder()
                .expireAfterWrite(localTtlMillis, TimeUnit.MILLISECONDS)
                .maximumSize(MAX_ENTRIES)
                .build();
        this.recentFailures = Caffeine.newBuilder()
                .expireAfterWrite(failureBackoffMillis, TimeUnit.MILLISECONDS)
                .maximumSize(MAX_ENTRIES)
                .build();
    }

    /**
     * 快照是否已就绪（且未过期）
     *
     * @param userId 用户ID
     * @return 就绪返回 true
     */
    public boolean isWarm(Long userId) {
        return userId != null && snapshots.getIfPresent(userId) != null;
    }

    /**
     * 纯内存读取快照（<b>不做任何 IO</b>）
     *
     * @param userId 用户ID
     * @return 快照；未就绪或已过期时返回 null
     */
    public PermissionSnapshot getSnapshot(Long userId) {
        if (userId == null) {
            return null;
        }
        return snapshots.getIfPresent(userId);
    }

    /**
     * 确保快照就绪：本地未命中时查库，并把结果填进本地缓存。
     *
     * <p>同一用户的并发调用只打一次（single-flight）；
     * 刚失败过的用户处在退避窗口里，期间直接跳过（不再打库）。
     *
     * @param userId 用户ID
     * @return 就绪后 complete
     */
    public Mono<Void> ensureWarm(Long userId) {
        if (userId == null) {
            return Mono.empty();
        }
        if (isWarm(userId)) {
            return Mono.empty();
        }
        if (isBackingOff(userId)) {
            return Mono.empty();
        }
        // computeIfAbsent 保证同一用户同一时刻只有一个刷新在飞；
        // doFinally 在源终止时把飞行中的条目摘掉（.cache() 之后订阅者仍能重放结果）。
        return warmFlights.computeIfAbsent(userId, id -> loadFromDb(id)
                .doFinally(signal -> warmFlights.remove(id))
                .cache());
    }

    /**
     * 反应式刷新快照：解析权限 → 写入内存
     *
     * <p>同一用户的并发刷新只打一次库（single-flight）；刚失败过的用户处在退避窗口里，
     * 期间直接跳过（不再打库）。
     *
     * @param userId 用户ID
     * @return 刷新完成后 complete
     */
    public Mono<Void> refresh(Long userId) {
        if (userId == null) {
            return Mono.empty();
        }
        return refreshFlights.computeIfAbsent(userId, id -> Mono.defer(() -> {
                    // <b>本地快照立即作废</b>：即使下面查库失败，也不能再用旧的那一份。
                    //
                    // 这一步必须在链的<b>最前面</b>、且<b>不依赖任何 IO</b>：
                    // 挂在某个「可能失败的操作」之后的话，那步失败就等于
                    // 「撤销权限被整条吞掉」，而调用方照样拿到 200。
                    snapshots.invalidate(userId);
                    return loadFromDb(id);
                })
                .doFinally(signal -> refreshFlights.remove(id))
                .cache());
    }

    /**
     * 真正去查库，并写入本地快照。
     *
     * <p>查库失败时<b>不写快照</b> —— 鉴权侧读到的就是空集合，
     * 于是 fail-closed（拒绝），而不是「按旧权限继续放行」。
     */
    private Mono<Void> loadFromDb(Long userId) {
        if (isBackingOff(userId)) {
            // 退避窗口内不查库（避免把 DB 抖动放大成查询风暴）。
            return Mono.empty();
        }
        // ⚠️ 必须<b>顺序</b>取这两条查询，不能用 zipWith/flatMap 并发：
        // 本方法会被 @Transactional 的方法调用（角色/权限变更后刷新快照），
        // 整条链绑定在同一条 R2DBC 连接上，而 r2dbc-mysql 1.4.0 每条连接只有一个
        // SPSC 的 RequestQueue，且 submit() 时不加锁 —— 并发提交会误判队满，
        // 抛 IllegalStateException: Request queue is full（见 CLAUDE.md）。
        return resolver.resolveRoleCodes(userId)
                .flatMap(roles -> resolver.resolvePermissionIdentities(userId)
                        .map(permissions -> new PermissionSnapshot(roles, permissions)))
                .doOnNext(snapshot -> snapshots.put(userId, snapshot))
                .doOnError(e -> {
                    recentFailures.put(userId, System.currentTimeMillis());
                    // 必须把异常对象本身传进去：只打 getMessage() 会把异常类型和堆栈全部丢掉。
                    // 这个位置尤其要紧 —— 刷新失败会被 onErrorResume 吞掉，表现为「突然所有权限都没了」，
                    // 而日志里只有一句没有堆栈的说明，现场根本无从判断是数据库连不上还是解析逻辑炸了。
                    log.warn("刷新用户权限快照失败: userId={}, 原因: {}", userId, e.getMessage(), e);
                })
                // 失败时快照保持不可用 —— 鉴权侧 fail-closed（拒绝），而不是放行。
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    /**
     * 是否处在失败退避窗口里。
     */
    private boolean isBackingOff(Long userId) {
        if (recentFailures.getIfPresent(userId) == null) {
            return false;
        }
        log.debug("用户权限快照刷新失败仍在退避窗口内，本次跳过: userId={}", userId);
        return true;
    }

    /**
     * 失效快照（用户被删除 / 禁用时调用）
     *
     * @param userId 用户ID
     * @return 失效完成后 complete
     */
    public Mono<Void> evict(Long userId) {
        if (userId != null) {
            snapshots.invalidate(userId);
            recentFailures.invalidate(userId);
        }
        return Mono.empty();
    }

    /**
     * 当前缓存里的快照条目数（估算）。
     *
     * <p>给护栏测试用：过期条目必须被真正淘汰，这个数才不会单调增长。
     *
     * @return 条目数
     */
    public long estimatedSize() {
        // 先做一次同步维护：Caffeine 的过期淘汰是异步的（维护周期最长可到一分钟），
        // 不先 cleanUp 的话，刚过期的条目还挂在表里 —— 那正是「内存无界」的假象。
        snapshots.cleanUp();
        return snapshots.estimatedSize();
    }
}
