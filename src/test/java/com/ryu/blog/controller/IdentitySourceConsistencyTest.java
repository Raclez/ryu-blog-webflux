package com.ryu.blog.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.reactor.context.SaReactorHolder;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.spring.SaTokenContextForSpringReactor;
import cn.dev33.satoken.reactor.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.config.SaPermissionAspect;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.utils.OperatorContexts;
import com.ryu.blog.utils.SaTokenUtils;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 同一个请求里，身份只能有一个。
 *
 * <p><b>它守的是什么</b>：本项目有两处能拿到「当前是谁」——
 * <ul>
 *   <li><b>ThreadLocal</b>（{@code SaTokenUtils.currentUserIdOrNull()}）：
 *       缓存 key 的 SpEL 只能同步取值，只能走这条；</li>
 *   <li><b>Reactor Context</b>（{@code OperatorContexts.from(cv)}）：
 *       归属校验（{@code OwnershipPolicy}）走这条。</li>
 * </ul>
 *
 * <p>两条路给出的答案必须一致 —— 不一致就是
 * 「<b>A 的可见性判定配 B 的缓存 key</b>」：key 说「这是 B 的数据」，判定却按 A 放行，
 * 结果<b>私有内容被写进别人的 key 下面</b>，之后谁拿到那个 key 谁就能读到。
 *
 * <p>它们一致的前提是：切面在调用 Controller 之前，用 <b>Reactor Context 里的
 * exchange</b>（本次请求的）重建 ThreadLocal —— 而不是用线程上恰好残留的那个。
 * 这就是 {@code SaPermissionAspectStaleContextTest} 守的那件事；
 * 本用例守的是它的推论：<b>重建之后，两条路必须给出同一个人</b>。
 *
 * @author ryu
 */
class IdentitySourceConsistencyTest {

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
        new SaTokenContextRegister();

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
        // 全局配置跨测试类共享：别的 SpringBootTest 加载 application.yml 之后
        // token-prefix 会变成 Bearer，这里裸传的 token 会被判「未按指定前缀提交」。
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
    @DisplayName("线程上残留着别人的上下文时，两条身份来源仍必须指向同一个人")
    void bothIdentitySourcesAgreeEvenWithStaleThreadLocal() {
        FakeController target = new FakeController();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new SaPermissionAspect());
        FakeController proxy = factory.getProxy();

        Long currentUserId = 1L;
        Long staleUserId = 2L;

        ServerWebExchange current = exchangeWithToken(issueToken(currentUserId));
        ServerWebExchange stale = exchangeWithToken(issueToken(staleUserId));

        // 线程上残留的是别人的请求
        SaReactorSyncHolder.setContext(stale);

        Mono<String> result = Mono.defer(() -> proxy.whoami())
                .contextWrite(ctx -> SaReactorHolder.setContext(ctx, current, e -> Mono.empty()))
                .contextWrite(ctx -> ctx.put(OperatorContexts.KEY, new OperatorContext(currentUserId, true)));

        StepVerifier.create(result)
                .expectNext("ok")
                .verifyComplete();

        assertEquals(currentUserId, target.reactorContextUserId,
                "Reactor Context 里的身份应当是本次请求的用户");
        assertEquals(currentUserId, target.threadLocalUserId,
                "ThreadLocal 里的身份必须与 Reactor Context 一致 —— "
                        + "否则就是「A 的可见性判定配 B 的缓存 key」");
    }

    private ServerWebExchange exchangeWithToken(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/whoami").header(TEST_TOKEN_HEADER, token).build());
    }

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

        private Long threadLocalUserId;
        private Long reactorContextUserId;

        @SaCheckPermission(TEST_PERMISSION)
        public Mono<String> whoami() {
            // ThreadLocal 那条路：缓存 key 的 SpEL 走的就是它
            threadLocalUserId = SaTokenUtils.currentUserIdOrNull();
            return Mono.deferContextual(cv -> {
                // Reactor Context 那条路：归属校验走的就是它
                reactorContextUserId = OperatorContexts.from(cv).userId();
                return Mono.just("ok");
            });
        }
    }
}
