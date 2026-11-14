package com.ryu.blog.service.impl;

import com.ryu.blog.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 权限快照的 <b>Redis 共享层</b>（L2）。
 *
 * <p><b>为什么要有它</b>：权限快照原先只存在进程内（{@code UserPermissionCache} 的 Caffeine），
 * 于是多实例部署时「撤销某个角色」最多要等一个 TTL 才在其它实例上生效 ——
 * 撤销权限不是即时的，这本身就是个安全窗口。
 *
 * <p>有了这层之后：
 * <ul>
 *   <li>鉴权读路径：L1（本地 Caffeine）→ L2（本文件）→ DB；</li>
 *   <li>权限变更时：本实例 refresh 会把新快照写回 L2，
 *       其它实例的 L1 过期后从 L2 读到的是<b>新的</b>，不必再各自查库；</li>
 *   <li>删除用户 / 禁用用户时：本实例 evict 会 DEL 掉 L2 的 key。</li>
 * </ul>
 *
 * <p><b>连接说明</b>：用的是应用自己的 {@code reactiveStringRedisTemplate}
 * （{@code config/RedisConfig}），<b>不是</b> Sa-Token 的 {@code alone-redis} ——
 * 后者是框架自己管的阻塞 {@code StringRedisTemplate}，序列化配置也不一样。
 * key 前缀 {@code perm:snap:} 与 Sa-Token 的 session key 隔开。
 *
 * <p><b>读不懂就当没有</b>：Redis 里可能是旧版本写的、或被手工改坏的数据，
 * 反序列化失败一律当作「缓存未命中」，重新查库 —— 不因为一条脏数据把整个鉴权打挂。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
public class PermissionSnapshotStore {

    /**
     * Redis key 前缀。
     */
    public static final String KEY_PREFIX = "perm:snap:";

    private final ReactiveRedisTemplate<String, String> redis;

    private final Duration ttl;

    public PermissionSnapshotStore(
            ReactiveRedisTemplate<String, String> redis,
            @Value("${blog.auth.permission-snapshot-redis-ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * 从 Redis 读快照。
     *
     * @param userId 用户ID
     * @return 快照；未命中或数据损坏时是空 Mono
     */
    public Mono<PermissionSnapshot> load(Long userId) {
        return redis.opsForValue().get(key(userId))
                .map(this::parse)
                .onErrorResume(e -> {
                    log.warn("读取权限快照失败，当作未命中: userId={}, 原因: {}", userId, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * 把快照写回 Redis。
     *
     * @param userId   用户ID
     * @param snapshot 快照
     * @return 写完后 complete
     */
    public Mono<Void> save(Long userId, PermissionSnapshot snapshot) {
        return redis.opsForValue().set(key(userId), JsonUtils.toJsonString(snapshot), ttl)
                .then()
                .onErrorResume(e -> {
                    // 写不进去不算致命：本地 L1 已经有这份快照了，只是其它实例看不到。
                    log.warn("写入权限快照到 Redis 失败: userId={}, 原因: {}", userId, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * 删除 Redis 里的快照。
     *
     * @param userId 用户ID
     * @return 删完后 complete
     */
    public Mono<Void> delete(Long userId) {
        return redis.delete(key(userId))
                .then()
                .onErrorResume(e -> {
                    log.warn("删除权限快照失败: userId={}, 原因: {}", userId, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * Redis 里的 key。
     */
    public String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    /**
     * 反序列化。数据损坏（旧版本格式、手工改坏）时当作未命中。
     */
    private PermissionSnapshot parse(String json) {
        try {
            PermissionSnapshot snapshot = JsonUtils.parseObject(json, PermissionSnapshot.class);
            return snapshot == null ? PermissionSnapshot.empty() : snapshot;
        } catch (Exception e) {
            log.warn("权限快照反序列化失败，当作未命中: 原因: {}", e.getMessage());
            return PermissionSnapshot.empty();
        }
    }
}
