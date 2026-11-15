package com.ryu.blog.config;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.exception.NotPermissionException;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.exception.GlobalExceptionHandler;
import com.ryu.blog.service.impl.PermissionSnapshot;
import com.ryu.blog.service.impl.UserPermissionCache;
import com.ryu.blog.utils.OperatorContexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 能力校验过滤器的行为护栏。
 *
 * <p><b>它守的是什么</b>：集成测试只覆盖了「没有权限 → 403」这一侧
 * （{@code RbacAccessIntegrationTest}、{@code PermissionBeforeBodyParsingTest}）。
 * 另一侧 —— <b>有权限 → 必须放行</b> —— 没有任何测试覆盖：
 * 万一过滤器写成了「一律拒绝」，现有测试<b>照样全绿</b>，而线上表现为
 * 「所有人都 403」。
 *
 * <p>这个用例把两侧都钉住。
 *
 * @author ryu
 */
class PermissionCheckFilterTest {

    private UserPermissionCache permissionCache;
    private RequestMappingHandlerMapping handlerMapping;
    private GlobalExceptionHandler globalExceptionHandler;
    private PermissionCheckFilter filter;

    @BeforeEach
    void setUp() {
        permissionCache = mock(UserPermissionCache.class);
        handlerMapping = mock(RequestMappingHandlerMapping.class);
        // 真实的全局异常处理器（它没有依赖，可以直接 new）——
        // 这样断言「body 是它渲染的」才有意义。
        globalExceptionHandler = new GlobalExceptionHandler();
        filter = new PermissionCheckFilter(permissionCache, globalExceptionHandler, handlerMapping);
    }

    @Test
    @DisplayName("快照里有这个权限 → 放行")
    void grantsWhenPermissionIsOwned() throws Exception {
        givenSnapshot(Set.of("system:user:add"));
        givenHandler(FakeController.class.getMethod("needsAdd"));

        AtomicBoolean chainCalled = new AtomicBoolean();
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        ServerWebExchange exchange = exchange();
        filter.filter(exchange, chain)
                .contextWrite(ctx -> ctx.put(OperatorContexts.KEY, new OperatorContext(1L, false)))
                .block();

        assertTrue(chainCalled.get(), "有权限就必须放行 —— 过滤器写成「一律拒绝」的话，线上就是所有人都 403");
        assertFalse(exchange.getResponse().isCommitted());
    }

    @Test
    @DisplayName("快照里没有这个权限 → 403，且不放行到下游")
    void deniesWhenPermissionIsMissing() throws Exception {
        givenSnapshot(Set.of("system:user:view"));
        givenHandler(FakeController.class.getMethod("needsAdd"));

        AtomicBoolean chainCalled = new AtomicBoolean();
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        ServerWebExchange exchange = exchange();
        filter.filter(exchange, chain)
                .contextWrite(ctx -> ctx.put(OperatorContexts.KEY, new OperatorContext(1L, false)))
                .block();

        assertFalse(chainCalled.get(), "没有权限就不能进 Controller");
        assertEquals(403, exchange.getResponse().getStatusCode().value());
        // body 必须是由 GlobalExceptionHandler 渲染的 —— 与 Controller 里抛出的异常同一种
        assertTrue(bodyOf(exchange).contains("权限不足"),
                "403 的 body 必须由全局异常处理渲染，实际: " + bodyOf(exchange));
    }

    @Test
    @DisplayName("没登录 → 403")
    void deniesAnonymous() throws Exception {
        givenSnapshot(Set.of("system:user:add"));
        givenHandler(FakeController.class.getMethod("needsAdd"));

        AtomicBoolean chainCalled = new AtomicBoolean();
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        ServerWebExchange exchange = exchange();
        filter.filter(exchange, chain)
                .contextWrite(ctx -> ctx.put(OperatorContexts.KEY, OperatorContext.ANONYMOUS))
                .block();

        assertFalse(chainCalled.get());
        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }

    @Test
    @DisplayName("端点没有能力要求 → 放行（不查快照）")
    void passesWhenNoRequirement() throws Exception {
        givenSnapshot(Set.of());
        givenHandler(FakeController.class.getMethod("noRequirement"));

        AtomicBoolean chainCalled = new AtomicBoolean();
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        ServerWebExchange exchange = exchange();
        filter.filter(exchange, chain)
                .contextWrite(ctx -> ctx.put(OperatorContexts.KEY, new OperatorContext(1L, false)))
                .block();

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("@SaIgnore 的端点 → 放行")
    void passesWhenIgnored() throws Exception {
        givenSnapshot(Set.of());
        givenHandler(FakeController.class.getMethod("ignored"));

        AtomicBoolean chainCalled = new AtomicBoolean();
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        ServerWebExchange exchange = exchange();
        filter.filter(exchange, chain)
                .contextWrite(ctx -> ctx.put(OperatorContexts.KEY, new OperatorContext(1L, false)))
                .block();

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("解析不出 handler → 放行（404 之类不该被鉴权拦下）")
    void passesWhenHandlerUnresolved() {
        when(handlerMapping.getHandler(any())).thenReturn(Mono.empty());

        AtomicBoolean chainCalled = new AtomicBoolean();
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        ServerWebExchange exchange = exchange();
        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    /**
     * 读出已经写进响应的 body。
     */
    private String bodyOf(ServerWebExchange exchange) {
        return ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block();
    }

    private void givenSnapshot(Set<String> permissions) {
        when(permissionCache.getSnapshot(1L))
                .thenReturn(new PermissionSnapshot(Set.of("USER"), permissions));
    }

    private void givenHandler(Method method) {
        when(handlerMapping.getHandler(any())).thenReturn(Mono.just(new HandlerMethod(new FakeController(), method)));
    }

    private ServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/users/save").build());
    }

    public static class FakeController {

        @SaCheckPermission("system:user:add")
        public Mono<String> needsAdd() {
            return Mono.just("ok");
        }

        public Mono<String> noRequirement() {
            return Mono.just("ok");
        }

        @SaIgnore
        public Mono<String> ignored() {
            return Mono.just("ok");
        }
    }
}
