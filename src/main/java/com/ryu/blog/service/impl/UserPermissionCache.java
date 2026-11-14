package com.ryu.blog.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 用户权限快照缓存（两级：本地 Caffeine + Redis 共享）
 *
 * <h3>三级读取路径</h3>
 * <pre>
 *   L1  本地 Caffeine（TTL 默认 5 秒）—— 鉴权路径只读它，零 IO
 *   L2  Redis（TTL 默认 5 分钟）        —— 多实例共享，权限撤销靠它收敛
 *   L3  数据库                          —— 权威来源
 * </pre>
 *
 * <p>L1 的 TTL 刻意做得很短：它只用来扛住「同一秒的连续请求」，
 * 真正的一致性交给 L2 —— 权限变更时新快照会写进 Redis，
 * 其它实例的 L1 最多几秒就过期并从 Redis 读到新的。
 * （要「毫秒级」生效还需要 Pub/Sub 广播失效，那一步没做。）
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
 * <p><b>一致性取舍</b>：进程内快照在多实例部署下有最长一个 TTL 的滞后。
 * 本实例内的角色/权限变更会主动 {@link #refresh(Long)} 刷新，
 * 其它实例最多滞后一个 TTL。这是「权限撤销」场景下的已知窗口，
 * 如需更强的一致性应改为 Redis 共享快照。
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
     * 快照默认有效期。取 60 秒：既能让多实例间的权限变更在一分钟内收敛，
     * 又不至于让每个请求都打一次数据库。
     */
    public static final long DEFAULT_LOCAL_TTL_MILLIS = 5_000L;

    /**
     * 刷新失败后的默认退避窗口。
     */
    public static final long DEFAULT_FAILURE_BACKOFF_MILLIS = 5_000L;

    /**
     * 快照条目数上限 —— 兜底，防止用户数异常增长时把内存吃光。
     */
    private static final long MAX_ENTRIES = 10_000L;

    private final UserPermissionResolver resolver;

    /**
     * L2：Redis 共享快照（多实例共享，权限撤销靠它收敛）。
     */
    private final PermissionSnapshotStore store;

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
     * 正在进行的刷新（single-flight）。同一用户的并发刷新共用同一个 Mono。
     */
    private final Map<Long, Mono<Void>> inFlight = new ConcurrentHashMap<>();

    public UserPermissionCache(
            UserPermissionResolver resolver,
            PermissionSnapshotStore store,
            @Value("${blog.auth.permission-snapshot-local-ttl-ms:5000}") long localTtlMillis,
            @Value("${blog.auth.permission-snapshot-backoff-ms:5000}") long failureBackoffMillis) {
        this.resolver = resolver;
        this.store = store;
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
     * 确保快照就绪：L1 未命中时依次问 L2（Redis）、L3（库），并把结果填进 L1。
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
        return inFlight.computeIfAbsent(userId, id -> loadThroughLayers(id)
                .doFinally(signal -> inFlight.remove(id))
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
        if (isBackingOff(userId)) {
            return Mono.empty();
        }
        return inFlight.computeIfAbsent(userId, id -> loadFromDb(id)
                .doFinally(signal -> inFlight.remove(id))
                .cache());
    }

    /**
     * L1 → L2（Redis） → L3（库）的读取路径。
     */
    private Mono<Void> loadThroughLayers(Long userId) {
        return store.load(userId)
                .flatMap(snapshot -> {
                    snapshots.put(userId, snapshot);
                    return Mono.just(Boolean.TRUE);
                })
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(hit -> hit ? Mono.empty() : loadFromDb(userId));
    }

    /**
     * 真正去查库，并写入 L1 与 L2（Redis）。
     */
    private Mono<Void> loadFromDb(Long userId) {
        return resolver.resolveRoleCodes(userId)
                .zipWith(resolver.resolvePermissionIdentities(userId), (roles, permissions) ->
                        new PermissionSnapshot(roles, permissions))
                .flatMap(snapshot -> {
                    snapshots.put(userId, snapshot);
                    // 写回 Redis：其它实例的 L1 过期后就能直接拿到这份新快照，
                    // 不用各自再查一次库。
                    return store.save(userId, snapshot);
                })
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
            // Redis 里的那份也删掉 —— 否则删用户 / 禁用用户之后，
            // 别的实例还能从共享快照里读到这份已失效的权限。
            return store.delete(userId);
        }
        return Mono.empty();
    }

    /**
     * 清空全部快照
     */
    public void evictAll() {
        snapshots.invalidateAll();
        recentFailures.invalidateAll();
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
