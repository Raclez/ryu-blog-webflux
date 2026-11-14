package com.ryu.blog.service.impl;

import com.ryu.blog.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 共享快照层的护栏。
 *
 * <p><b>它守的是什么</b>：这层是「多实例之间权限撤销能收敛」的关键。
 * 三个最容易写错的地方：
 * <ul>
 *   <li>key 前缀 —— 必须与 Sa-Token 自己的 session key 隔开，
 *       否则清缓存时会把登录态一起清掉；</li>
 *   <li>TTL —— 必须设，否则一条快照会在 Redis 里永远留着；</li>
 *   <li><b>读不懂就当没有</b> —— Redis 里可能是旧版本写的、或被手工改坏的数据。
 *       反序列化失败必须当作「未命中」重新查库，而不是把整个鉴权打挂。</li>
 * </ul>
 *
 * @author ryu
 */
class PermissionSnapshotStoreTest {

    private ReactiveRedisTemplate<String, String> redis;
    private ReactiveValueOperations<String, String> ops;
    private PermissionSnapshotStore store;

    @BeforeEach
    void setUp() {
        redis = mock(ReactiveRedisTemplate.class);
        ops = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.delete(anyString())).thenReturn(Mono.just(1L));
        store = new PermissionSnapshotStore(redis, 300L);
    }

    @Test
    @DisplayName("key 前缀与 Sa-Token 的 session key 隔开")
    void keyPrefixIsIsolated() {
        assertEquals("perm:snap:", PermissionSnapshotStore.KEY_PREFIX);
        assertEquals("perm:snap:42", store.key(42L));
    }

    @Test
    @DisplayName("读到的 JSON 能还原成快照")
    void loadParsesSnapshot() {
        PermissionSnapshot original = new PermissionSnapshot(Set.of("ADMIN"), Set.of("system:user:view"));
        when(ops.get("perm:snap:1")).thenReturn(Mono.just(JsonUtils.toJsonString(original)));

        PermissionSnapshot loaded = store.load(1L).block();

        assertEquals(Set.of("ADMIN"), loaded.roles());
        assertEquals(Set.of("system:user:view"), loaded.permissions());
    }

    @Test
    @DisplayName("Redis 里是坏数据时当作未命中，而不是把鉴权打挂")
    void corruptDataIsTreatedAsMiss() {
        when(ops.get("perm:snap:1")).thenReturn(Mono.just("{ 这不是 JSON"));

        PermissionSnapshot loaded = store.load(1L).block();

        assertTrue(loaded.roles().isEmpty(), "坏数据应当退化成空快照");
        assertTrue(loaded.permissions().isEmpty());
    }

    @Test
    @DisplayName("Redis 读失败时当作未命中")
    void redisFailureIsTreatedAsMiss() {
        when(ops.get("perm:snap:1")).thenReturn(Mono.error(new RuntimeException("redis down")));

        assertTrue(store.load(1L).block() == null, "Redis 挂了应当返回空 Mono（未命中），而不是把异常抛出去");
    }

    @Test
    @DisplayName("权限版本号自增")
    void bumpEpochIncrements() {
        when(ops.increment("perm:epoch:1")).thenReturn(Mono.just(3L));

        assertEquals(3L, store.bumpEpoch(1L).block());
    }

    @Test
    @DisplayName("读版本号：没有记录时是 0")
    void currentEpochDefaultsToZero() {
        when(ops.get("perm:epoch:1")).thenReturn(Mono.empty());

        assertEquals(0L, store.currentEpoch(1L).block());
    }

    @Test
    @DisplayName("写 Redis 失败必须冒出去 —— 吞掉就等于静默地让旧权限继续生效")
    void saveFailureMustPropagate() {
        when(ops.set(eq("perm:snap:1"), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("redis down")));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> store.save(1L, new PermissionSnapshot(Set.of("ADMIN"), Set.of())).block());
    }

    @Test
    @DisplayName("删除失败必须冒出去 —— 吞掉就等于删用户后权限还在")
    void deleteFailureMustPropagate() {
        when(redis.delete("perm:snap:1")).thenReturn(Mono.error(new RuntimeException("redis down")));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> store.delete(1L).block());
    }

    @Test
    @DisplayName("读版本号失败必须冒出去 —— 当作 0 就等于无法判断快照是不是旧版")
    void epochReadFailureMustPropagate() {
        when(ops.get("perm:epoch:1")).thenReturn(Mono.error(new RuntimeException("redis down")));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> store.currentEpoch(1L).block());
    }

    @Test
    @DisplayName("写入必须带 TTL")
    void saveSetsTtl() {
        when(ops.set(eq("perm:snap:1"), anyString(), any(Duration.class))).thenReturn(Mono.just(Boolean.TRUE));

        store.save(1L, new PermissionSnapshot(Set.of("USER"), Set.of())).block();

        verify(ops, times(1)).set(eq("perm:snap:1"), anyString(), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("删除走 Redis 的 DEL")
    void deleteRemovesKey() {
        store.delete(1L).block();

        verify(redis, times(1)).delete("perm:snap:1");
    }
}
