package com.ryu.blog.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.spring.SaTokenContextForSpringReactor;
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
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 注解鉴权不能依赖「调用线程上刚好有 Sa-Token 上下文」。
 *
 * <p><b>它守的是什么</b>：{@code @RequestPart FilePart} 的参数解析是异步的，
 * multipart 落盘会把 Controller 的调用切到 boundedElastic（或另一个 nio 线程）上，
 * 而 Sa-Token 的上下文是 ThreadLocal —— 切走之后 {@code StpUtil} 抛
 * {@code SaTokenContextException: 未能获取有效的上下文}，接口 500。
 *
 * <p>它的表现极具欺骗性：某个线程上恰好残留着上一次请求的上下文就成功，
 * 否则失败，看起来像「偶发的鉴权故障」。
 *
 * <p>本用例在一个<b>没有</b> ThreadLocal 上下文的线程上调用被切面代理的方法，
 * 只把 exchange 放进 Reactor Context —— 这正是线上真实的处境。
 *
 * @author ryu
 */
class SaPermissionAspectContextTest {

    private SaTokenContext previousContext;

    /**
     * 注册 WebFlux 的上下文实现。
     *
     * <p>{@link SaTokenContextForSpringReactor} 只有一句话要紧：它
     * <b>继承自 {@code SaTokenContextForThreadLocal}</b> —— 生产环境的鉴权上下文
     * 完全建立在 ThreadLocal 上，这正是一切问题的源头。
     * 不注册的话，单测里拿到的是什么都不做的默认实现，测不到真实语义。
     */
    @BeforeEach
    void registerReactorContext() {
        previousContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(new SaTokenContextForSpringReactor());
    }

    @AfterEach
    void restore() {
        SaManager.setSaTokenContext(previousContext);
        SaReactorSyncHolder.clearContext();
    }

    @Test
    @DisplayName("调用线程没有上下文时，从 Reactor Context 恢复后再鉴权，不再抛 SaTokenContextException")
    void restoresContextFromReactorContext() {
        FakeController target = new FakeController();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new SaPermissionAspect());
        FakeController proxy = factory.getProxy();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/files/upload").build());

        // 在 boundedElastic 线程上调用代理方法：该线程上没有任何 Sa-Token ThreadLocal
        Mono<String> result = Mono.defer(() -> proxy.upload())
                .subscribeOn(Schedulers.boundedElastic())
                .contextWrite(ctx -> ctx.put(ServerWebExchange.class, exchange));

        StepVerifier.create(result)
                .expectErrorSatisfies(e ->
                        assertFalse(e instanceof SaTokenContextException,
                                "上下文没有从 Reactor Context 恢复出来: " + e))
                .verify();

        // 鉴权没过（没登录），业务方法一次都不该被调用
        assertFalse(target.invoked, "鉴权失败时不应执行 Controller 方法");
    }

    /**
     * 假的 Controller：放在 controller 包下，才能被切面的
     * {@code execution(public * com.ryu.blog.controller..*(..))} 命中。
     */
    public static class FakeController {

        private boolean invoked;

        @SaCheckLogin
        public Mono<String> upload() {
            invoked = true;
            return Mono.just("ok");
        }
    }
}
