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

    /**
     * 权限版本号（epoch）的 key 前缀。每个用户一个。
     */
    public static final String EPOCH_KEY_PREFIX = "perm:epoch:";

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
        // <b>不吞异常</b>：写不进去意味着别的实例读到的还是旧版权限。
        // 吞掉它 = 静默地让已撤销的权限继续生效。错误必须冒到调用方，
        // 由它决定这次变更算不算成功（并且要留日志）。
        return redis.opsForValue().set(key(userId), JsonUtils.toJsonString(snapshot), ttl)
                .then();
    }

    /**
     * 读当前用户的权限版本号。
     *
     * @param userId 用户ID
     * @return 版本号；没有记录时是 0
     */
    public Mono<Long> currentEpoch(Long userId) {
        return redis.opsForValue().get(epochKey(userId))
                .map(this::parseEpoch)
                .defaultIfEmpty(0L)
                .onErrorResume(e -> {
                    // 读不到版本号 = 当作 0。配合快照里的 epoch 比较：
                    // 快照的 epoch 会大于 0，于是会被判为「旧版」→ 回库重取（fail-closed）。
                    log.warn("读取权限版本号失败，当作 0: userId={}, 原因: {}", userId, e.getMessage(), e);
                    return Mono.just(0L);
                });
    }

    /**
     * 权限版本号自增 —— <b>权限变更的第一步</b>。
     *
     * <p>自增之后，所有已存在的旧快照（它们带的 epoch 更小）都会被判定为过期，
     * 各实例读的时候会回库重取。这样即使「写新快照到 Redis」这一步失败了，
     * 也能自愈：旧快照不会被继续信任。
     *
     * @param userId 用户ID
     * @return 自增后的版本号
     */
    public Mono<Long> bumpEpoch(Long userId) {
        return redis.opsForValue().increment(epochKey(userId))
                .onErrorResume(e -> {
                    // 版本号自增失败 = 这次变更<b>无法</b>被别的实例感知到。
                    // 必须冒出去：调用方要看到「变更没有生效到共享层」。
                    log.error("权限版本号自增失败，本次权限变更未能生效到共享层: userId={}, 原因: {}",
                            userId, e.getMessage(), e);
                    return Mono.error(e);
                });
    }

    /**
     * 删除 Redis 里的快照。
     *
     * @param userId 用户ID
     * @return 删完后 complete
     */
    public Mono<Void> delete(Long userId) {
        // <b>不吞异常</b>：删不掉意味着别的实例还能从共享层读到已失效的权限。
        // 冒出去，让调用方知道这次失效没有生效到共享层。
        return redis.delete(key(userId))
                .then(redis.delete(epochKey(userId)))
                .then();
    }

    /**
     * Redis 里的 key。
     */
    public String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    /**
     * 权限版本号的 key。
     */
    public String epochKey(Long userId) {
        return EPOCH_KEY_PREFIX + userId;
    }

    private long parseEpoch(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
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
