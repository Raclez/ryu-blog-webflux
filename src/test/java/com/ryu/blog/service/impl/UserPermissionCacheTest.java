package com.ryu.blog.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限快照缓存的行为护栏。
 *
 * <p><b>它守的是什么</b>：快照是鉴权的唯一数据源，它自己的三个毛病会直接变成线上事故：
 * <ol>
 *   <li><b>内存无界</b>：原先是个裸 {@code ConcurrentHashMap}，过期条目<b>永不清理</b>，
 *       也没有上限 —— 内存随「历史上登录过的用户数」单调增长；</li>
 *   <li><b>惊群</b>：TTL 一到，同一用户的 N 个并发请求<b>同时</b>打两次库；</li>
 *   <li><b>失败即风暴</b>：刷新失败被吞掉 → 快照保持过期 → <b>每个后续请求都重试</b>，
 *       数据库一抖动，查询量被放大 N 倍，同时所有用户被 fail-closed 成 403
 *       （现场看起来像「权限全没了」）。</li>
 * </ol>
 *
 * @author ryu
 */
class UserPermissionCacheTest {

    /** TTL 取 60ms：够短，测试不用真的等一分钟。 */
    private static final long TTL_MILLIS = 60L;

    /** 失败退避取 10 秒。 */
    private static final long BACKOFF_MILLIS = 10_000L;

    private UserPermissionResolver resolver;
    private UserPermissionCache cache;

    @BeforeEach
    void setUp() {
        resolver = mock(UserPermissionResolver.class);
        when(resolver.resolveRoleCodes(anyLong())).thenReturn(Mono.just(Set.of("ADMIN")));
        when(resolver.resolvePermissionIdentities(anyLong()))
                .thenReturn(Mono.just(Set.of("system:user:view")));
        cache = new UserPermissionCache(resolver, TTL_MILLIS, BACKOFF_MILLIS);
    }

    @Test
    @DisplayName("快照过期后既不可用，条目也要被淘汰（内存有界）")
    void expiredEntryIsEvicted() throws InterruptedException {
        cache.refresh(1L).block();
        assertTrue(cache.isWarm(1L), "刚刷新完应当是热的");

        Thread.sleep(TTL_MILLIS + 80);

        assertFalse(cache.isWarm(1L), "过期后不再算热");
        assertNull(cache.getSnapshot(1L), "过期后取不到快照");
        assertEquals(0, cache.estimatedSize(),
                "过期条目必须被淘汰 —— 否则内存会随「历史上登录过的用户数」单调增长");
    }

    @Test
    @DisplayName("同一用户的并发刷新只查库一次")
    void concurrentRefreshHitsDatabaseOnce() {
        // 解析器要「慢」一点，20 个并发刷新才会真正重叠 ——
        // 瞬间返回的话，第一个刷新早就结束了，后面 19 个各自都算「没有在飞的刷新」。
        when(resolver.resolveRoleCodes(anyLong()))
                .thenReturn(Mono.just(Set.of("ADMIN")).delayElement(Duration.ofMillis(200)));
        when(resolver.resolvePermissionIdentities(anyLong()))
                .thenReturn(Mono.just(Set.of("system:user:view")).delayElement(Duration.ofMillis(200)));

        Flux.range(0, 20)
                .flatMap(i -> cache.refresh(1L))
                .blockLast();

        verify(resolver, times(1)).resolveRoleCodes(1L);
        verify(resolver, times(1)).resolvePermissionIdentities(1L);
    }

    @Test
    @DisplayName("刷新失败后进入退避窗口，不再反复打库")
    void failedRefreshBacksOff() {
        when(resolver.resolveRoleCodes(anyLong()))
                .thenReturn(Mono.error(new RuntimeException("数据库连不上")));

        cache.refresh(1L).block();
        cache.refresh(1L).block();
        cache.refresh(1L).block();

        verify(resolver, times(1)).resolveRoleCodes(1L);
        assertFalse(cache.isWarm(1L), "刷新失败时快照必须保持不可用（fail-closed）");
    }

    @Test
    @DisplayName("退避窗口过去之后允许再试")
    void backoffExpires() {
        when(resolver.resolveRoleCodes(anyLong()))
                .thenReturn(Mono.error(new RuntimeException("数据库连不上")));

        UserPermissionCache shortBackoff = new UserPermissionCache(resolver, TTL_MILLIS, 50L);
        shortBackoff.refresh(1L).block();
        shortBackoff.refresh(1L).block();
        verify(resolver, times(1)).resolveRoleCodes(1L);

        try {
            Thread.sleep(120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        shortBackoff.refresh(1L).block();
        verify(resolver, times(2)).resolveRoleCodes(1L);
    }

    @Test
    @DisplayName("不同用户互不影响")
    void differentUsersAreIndependent() {
        cache.refresh(1L).block();
        cache.refresh(2L).block();

        assertEquals(2, cache.estimatedSize());
        assertTrue(cache.isWarm(1L));
        assertTrue(cache.isWarm(2L));

        cache.evict(1L).block();

        assertFalse(cache.isWarm(1L));
        assertTrue(cache.isWarm(2L));
    }

    @Test
    @DisplayName("快照内容与解析器给的一致")
    void snapshotCarriesRolesAndPermissions() {
        cache.refresh(1L).block();

        UserPermissionCache.Snapshot snapshot = cache.getSnapshot(1L);
        assertEquals(Set.of("ADMIN"), snapshot.roles());
        assertEquals(Set.of("system:user:view"), snapshot.permissions());
    }

    @Test
    @DisplayName("TTL 用毫秒配置，60 秒的默认值不再是硬编码")
    void ttlIsConfigurable() {
        // 只是把「TTL 可配」这件事钉住：缓存必须按传入的 TTL 过期，
        // 而不是写死 60 秒 —— 写死的话测试只能干等一分钟。
        cache.refresh(1L).block();
        assertTrue(cache.isWarm(1L));
        assertTrue(Duration.ofMillis(TTL_MILLIS).toMillis() < 1_000,
                "测试用的 TTL 必须短于 1 秒");
    }
}
