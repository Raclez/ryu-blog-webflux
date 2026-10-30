package com.ryu.blog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户权限快照缓存（进程内）
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
 * <p><b>一致性取舍</b>：进程内快照在多实例部署下有最长 {@link #TTL_MILLIS} 的滞后。
 * 本实例内的角色/权限变更会主动 {@link #refresh(Long)} 刷新，
 * 其它实例最多滞后一个 TTL。这是「权限撤销」场景下的已知窗口，
 * 如需更强的一致性应改为 Redis 共享快照。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPermissionCache {

    /**
     * 快照有效期。取 60 秒：既能让多实例间的权限变更在一分钟内收敛，
     * 又不至于让每个请求都打一次数据库。
     */
    public static final long TTL_MILLIS = 60_000L;

    private final UserPermissionResolver resolver;

    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    /**
     * 权限快照
     *
     * @param roles       角色码集合
     * @param permissions 权限标识集合
     */
    public record Snapshot(Set<String> roles, Set<String> permissions) {
        public static Snapshot empty() {
            return new Snapshot(Set.of(), Set.of());
        }
    }

    private record Entry(Snapshot snapshot, long loadedAt) {
    }

    /**
     * 快照是否已就绪（且未过期）
     *
     * @param userId 用户ID
     * @return 就绪返回 true
     */
    public boolean isWarm(Long userId) {
        Entry entry = entries.get(userId);
        return entry != null && !isExpired(entry);
    }

    /**
     * 纯内存读取快照（<b>不做任何 IO</b>）
     *
     * @param userId 用户ID
     * @return 快照；未就绪或已过期时返回 null
     */
    public Snapshot getSnapshot(Long userId) {
        if (userId == null) {
            return null;
        }
        Entry entry = entries.get(userId);
        if (entry == null || isExpired(entry)) {
            return null;
        }
        return entry.snapshot();
    }

    /**
     * 反应式刷新快照：解析权限 → 写入内存
     *
     * @param userId 用户ID
     * @return 刷新完成后 complete
     */
    public Mono<Void> refresh(Long userId) {
        if (userId == null) {
            return Mono.empty();
        }
        return resolver.resolveRoleCodes(userId)
                .zipWith(resolver.resolvePermissionIdentities(userId), (roles, permissions) ->
                        new Snapshot(roles, permissions))
                .doOnNext(snapshot -> entries.put(userId, new Entry(snapshot, System.currentTimeMillis())))
                // 必须把异常对象本身传进去：只打 getMessage() 会把异常类型和堆栈全部丢掉。
                // 这个位置尤其要紧 —— 刷新失败会被 onErrorResume 吞掉，表现为「突然所有权限都没了」，
                // 而日志里只有一句没有堆栈的说明，现场根本无从判断是数据库连不上还是解析逻辑炸了。
                .doOnError(e -> log.warn("刷新用户权限快照失败: userId={}, 原因: {}", userId, e.getMessage(), e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    /**
     * 失效快照（用户被删除 / 禁用时调用）
     *
     * @param userId 用户ID
     * @return 失效完成后 complete
     */
    public Mono<Void> evict(Long userId) {
        if (userId != null) {
            entries.remove(userId);
        }
        return Mono.empty();
    }

    /**
     * 清空全部快照
     */
    public void evictAll() {
        entries.clear();
    }

    private boolean isExpired(Entry entry) {
        return System.currentTimeMillis() - entry.loadedAt() > TTL_MILLIS;
    }
}
