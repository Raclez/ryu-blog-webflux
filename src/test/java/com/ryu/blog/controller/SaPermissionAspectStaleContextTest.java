package com.ryu.blog.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.context.SaReactorHolder;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.spring.SaTokenContextForSpringReactor;
import cn.dev33.satoken.reactor.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.config.SaPermissionAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴权必须以「本次请求」为准，不能以「当前线程上恰好残留的上下文」为准。
 *
 * <p><b>它守的是什么</b>：{@code SaTokenContextForSpringReactor} 继承自
 * {@code SaTokenContextForThreadLocal} —— Sa-Token 的上下文是纯 ThreadLocal，
 * 而 ThreadLocal 挂在池化线程上，请求结束后不一定被清掉。
 *
 * <p>于是「当前线程上有没有 box」<b>不等于</b>「这个 box 是不是本次请求的」。
 * 原来的实现用前者当后者的判据（{@code isSaTokenContextPresent()}），
 * 命中就走快路径，直接用<b>上一个请求</b>的 token 做判定 ——
 * 上一个请求是管理员，这一次普通用户就被放行；反过来上一次匿名，
 * 这一次合法用户被判 401。
 *
 * <p>本用例把「残留上下文」和「本次请求」拆成两个 exchange：
 * 残留的那个放在 ThreadLocal 上，本次请求的那个放在 Reactor Context 里，
 * 断言鉴权结果只由 Reactor Context 决定。
 *
 * @author ryu
 */
class SaPermissionAspectStaleContextTest {

    /**
     * 测试专用的 token 头 —— 单测里不加载 application.yml，
     * Sa-Token 的默认 token 名是 "satoken"，这里换成一个不会撞上的名字。
     */
    private static final String TEST_TOKEN_HEADER = "X-Test-Token";

    private static final String TEST_PERMISSION = "test:some-permission";

    private SaTokenContext previousContext;
    private StpInterface previousStpInterface;
    private String previousTokenName;
    private String previousTokenPrefix;

    @BeforeEach
    void setUp() {
        previousContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(new SaTokenContextForSpringReactor());
        // 1.46 的 SaReactorSyncHolder.setContext 走 SaStrategy 里的
        // createSaRequest/createSaResponse/createSaStorage —— 这三个策略由
        // SaTokenContextRegister 的构造函数装上（生产环境由 starter 自动配置装）。
        // 单测不启动 Spring，得自己装一次，否则 setContext 直接抛 NotImplException。
        new SaTokenContextRegister();

        // 权限判定一律放行 —— 本用例验证的是「判定用的是哪个 exchange」，
        // 不是权限本身。真正的权限判定由 RbacAccessIntegrationTest 守。
        previousStpInterface = SaManager.getStpInterface();
        SaManager.setStpInterface(new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return List.of(TEST_PERMISSION);
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        });

        previousTokenName = SaManager.getConfig().getTokenName();
        SaManager.getConfig().setTokenName(TEST_TOKEN_HEADER);

        // token 前缀也要清掉：Sa-Token 的全局配置是跨测试类共享的，
        // 别的 SpringBootTest 加载了 application.yml（token-prefix: Bearer）之后，
        // 这里裸传的 token 会被判成「未按指定前缀提交 token」。
        previousTokenPrefix = SaManager.getConfig().getTokenPrefix();
        SaManager.getConfig().setTokenPrefix(null);
    }

    @AfterEach
    void tearDown() {
        SaManager.setSaTokenContext(previousContext);
        SaManager.setStpInterface(previousStpInterface);
        SaManager.getConfig().setTokenName(previousTokenName);
        SaManager.getConfig().setTokenPrefix(previousTokenPrefix);
        SaReactorSyncHolder.clearContext();
    }

    @Test
    @DisplayName("线程上残留着匿名上下文时，鉴权必须以 Reactor Context 里的本次请求为准")
    void staleAnonymousContextMustNotWin() {
        FakeController target = new FakeController();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new SaPermissionAspect());
        FakeController proxy = factory.getProxy();

        // 本次请求：带有效 token
        String token = issueToken(1L);
        ServerWebExchange fresh = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin").header(TEST_TOKEN_HEADER, token).build());

        // 线程上残留：上一个请求是匿名的（没有 token）
        SaReactorSyncHolder.setContext(MockServerWebExchange.from(
                MockServerHttpRequest.get("/public").build()));

        Mono<String> result = Mono.defer(() -> proxy.adminOnly())
                .contextWrite(ctx -> SaReactorHolder.setContext(ctx, fresh, e -> Mono.empty()));

        StepVerifier.create(result)
                .expectNext("ok")
                .verifyComplete();

        assertTrue(target.invoked, "本次请求带着有效 token，应当放行 —— 残留的匿名上下文不该把它拦下来");
    }

    @Test
    @DisplayName("线程上残留着别人的 token 时，不能拿它给本次请求放行")
    void staleTokenMustNotGrantAccess() {
        FakeController target = new FakeController();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new SaPermissionAspect());
        FakeController proxy = factory.getProxy();

        // 线程上残留：上一个请求带着有效 token（可能是别人的）
        String staleToken = issueToken(1L);
        SaReactorSyncHolder.setContext(MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin").header(TEST_TOKEN_HEADER, staleToken).build()));

        // 本次请求：匿名
        ServerWebExchange fresh = MockServerWebExchange.from(
                MockServerHttpRequest.get("/public").build());

        Mono<String> result = Mono.defer(() -> proxy.adminOnly())
                .contextWrite(ctx -> SaReactorHolder.setContext(ctx, fresh, e -> Mono.empty()));

        StepVerifier.create(result)
                .expectError(NotLoginException.class)
                .verify();

        assertFalse(target.invoked, "残留的 token 不能给本次请求放行");
    }

    /**
     * 签发一个 token。
     *
     * <p>用的是 Sa-Token 的进程内默认 DAO（单测不启动 Spring，不会有 Redis DAO），
     * token 写在当前 exchange 的 storage 里，{@code getTokenValue()} 读得回来。
     */
    private String issueToken(Long userId) {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());
        SaReactorSyncHolder.setContext(exchange);
        try {
            StpUtil.login(userId);
            return StpUtil.getTokenValue();
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

    /**
     * 假的 Controller：放在 controller 包下，才能被切面的
     * {@code execution(public * com.ryu.blog.controller..*(..))} 命中。
     */
    public static class FakeController {

        private boolean invoked;

        @SaCheckPermission(TEST_PERMISSION)
        public Mono<String> adminOnly() {
            invoked = true;
            return Mono.just("ok");
        }
    }
}
