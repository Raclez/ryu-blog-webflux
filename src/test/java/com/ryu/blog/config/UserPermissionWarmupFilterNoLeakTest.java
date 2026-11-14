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
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 预热过滤器不得在请求线程上留下 Sa-Token 上下文。
 *
 * <p><b>它守的是什么</b>：{@code UserPermissionWarmupFilter.resolveLoginId} 为了读 token
 * 会先 {@code SaReactorSyncHolder.setContext(exchange)}，但那个上下文<b>建完就不管了</b>。
 * 线程是池化的（Netty event-loop / R2DBC event-loop），请求结束后那个 box 还留在线程上 ——
 * 于是下一个请求跑在这个线程上时，「当前线程上有没有 box」这件事就失去了意义。
 *
 * <p>这正是 {@code SaPermissionAspect} 曾经栽的坑：它拿「有没有 box」当「box 是不是我的」
 * 的判据，于是用上一个请求的 token 判权限。切面已经改成无条件从 Reactor Context 恢复
 * （见 {@code SaPermissionAspectStaleContextTest}），但让预热过滤器不再制造这种残留，
 * 是同一件事的两面：<b>上下文只在用它的那个作用域里存在</b>。
 *
 * <p>注意：现有的 {@code UserPermissionWarmupFilterContextTest} 覆写了
 * {@code resolveLoginId}，所以它<b>没有</b>覆盖到「建了上下文不清」这条路径 ——
 * 这个用例才是它的守门人。
 *
 * @author ryu
 */
class UserPermissionWarmupFilterNoLeakTest {

    private SaTokenContext previousContext;

    @BeforeEach
    void setUp() {
        previousContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(new SaTokenContextForSpringReactor());
        // 1.46 的 SaReactorSyncHolder.setContext 走 SaStrategy 里的 createSaRequest 等策略，
        // 生产环境由 starter 的 SaTokenContextRegister 构造函数装上，单测得自己装一次。
        new SaTokenContextRegister();
    }

    @AfterEach
    void tearDown() {
        SaManager.setSaTokenContext(previousContext);
        SaReactorSyncHolder.clearContext();
    }

    @Test
    @DisplayName("预热过滤器跑完之后，线程上不得残留 Sa-Token 上下文")
    void leavesNoContextBehind() {
        UserPermissionCache permissionCache = mock(UserPermissionCache.class);
        when(permissionCache.isWarm(7L)).thenReturn(true);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/posts/front").build());

        new UserPermissionWarmupFilter(permissionCache)
                .filter(exchange, e -> Mono.empty())
                .block();

        // 请求已经结束，线程上不该还挂着这次请求的 box
        assertThrows(Exception.class, SaReactorSyncHolder::getExchange,
                "预热过滤器在请求线程上留下了 Sa-Token 上下文 —— 下一个请求跑在这个线程上时，"
                        + "「有没有 box」就不能用来判断「box 是不是我的」");
    }
}
