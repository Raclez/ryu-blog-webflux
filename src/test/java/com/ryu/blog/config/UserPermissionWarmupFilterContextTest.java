package com.ryu.blog.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.spring.SaTokenContextForSpringReactor;
import cn.dev33.satoken.reactor.spring.SaTokenContextRegister;
import com.ryu.blog.service.impl.UserPermissionCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 权限快照预热不能把 Sa-Token 的上下文弄丢。
 *
 * <p><b>它守的是什么</b>：{@code UserPermissionWarmupFilter} 在快照过期（TTL 60 秒）时会
 * 走 R2DBC / Redis 刷新一次，然后 {@code .then(chain.filter(exchange))} 继续往下走 ——
 * 但续作是在 <b>另一个线程</b>上恢复的，而 Sa-Token 的响应式上下文
 * （{@code SaReactorSyncHolder}）是 <b>ThreadLocal</b>。
 * 结果就是：Controller 上的 {@code @SaCheckPermission} 在新线程上找不到上下文，
 * 抛 {@code SaTokenContextException: 未能获取有效的上下文}，接口 500。
 *
 * <p>表现极容易误导：同样的接口，快照命中时好、过期时炸；重启后第一个请求必炸，
 * 看起来像「偶发的鉴权故障」。
 *
 * @author ryu
 */
class UserPermissionWarmupFilterContextTest {

    private SaTokenContext previousContext;

    @BeforeEach
    void setUp() {
        previousContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(new SaTokenContextForSpringReactor());
        // 1.46 的 SaReactorSyncHolder.setContext 走 SaStrategy 里的 createSaRequest 等策略，
        // 生产环境由 starter 的 SaTokenContextRegister 构造函数装上。
        // 单测不启动 Spring，得自己装一次 —— 否则这个用例只能靠「别的 SpringBootTest
        // 先跑过、把全局策略装上了」才绿，单独跑必红（顺序依赖）。
        new SaTokenContextRegister();
    }

    @AfterEach
    void clearHolder() {
        SaManager.setSaTokenContext(previousContext);
        SaReactorSyncHolder.clearContext();
    }

    @Test
    @DisplayName("快照刷新在别的线程完成时，继续往下走之前必须重建 Sa-Token 上下文")
    void reestablishesContextAfterAsyncRefresh() {
        UserPermissionCache permissionCache = mock(UserPermissionCache.class);
        when(permissionCache.isWarm(7L)).thenReturn(false);
        // 模拟 refresh 走 R2DBC / Redis：续作在另一个线程上恢复
        when(permissionCache.ensureWarm(7L)).thenReturn(
                Mono.fromRunnable(() -> { }).subscribeOn(Schedulers.boundedElastic()).then());

        AtomicReference<String> chainThread = new AtomicReference<>();
        AtomicBoolean contextVisible = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> Mono.defer(() -> {
            chainThread.set(Thread.currentThread().getName());
            try {
                contextVisible.set(SaReactorSyncHolder.getExchange() != null);
            } catch (Exception e) {
                // 修复前就是这个分支：ThreadLocal 上没有 box，getExchange() 直接抛
                contextVisible.set(false);
            }
            return Mono.empty();
        });

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/files/upload").build());

        new TestableFilter(permissionCache).filter(exchange, chain).block();

        assertNotEquals(Thread.currentThread().getName(), chainThread.get(),
                "用例前提：链条必须在别的线程上继续，否则测不到线程切换");
        assertTrue(contextVisible.get(),
                "续作线程上没有 Sa-Token 上下文，Controller 的 @SaCheckPermission 会抛 SaTokenContextException");
    }

    private static final class TestableFilter extends UserPermissionWarmupFilter {

        private TestableFilter(UserPermissionCache permissionCache) {
            super(permissionCache);
        }

        @Override
        Long resolveLoginId(ServerWebExchange exchange) {
            return 7L;
        }
    }
}
