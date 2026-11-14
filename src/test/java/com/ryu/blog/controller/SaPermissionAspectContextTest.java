package com.ryu.blog.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.context.SaReactorHolder;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.spring.SaTokenContextForSpringReactor;
import cn.dev33.satoken.reactor.spring.SaTokenContextRegister;
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
 * <p>本用例在一个<b>没有</b> ThreadLocal 上下文的线程上调用被切面代理的方法，
 * 只把 exchange 放进 Reactor Context —— 这正是线上真实的处境。
 *
 * <p><b>为什么断言必须收紧</b>：早先这个用例只断言「抛的不是 SaTokenContextException」，
 * 而且把 exchange 放进 Reactor Context 时用的 key 是 {@code ServerWebExchange.class} ——
 * 而 {@code SaReactorHolder} 读的是 {@code SA_REACTOR_EXCHANGE_KEY}。
 * 两个错叠在一起，<b>把恢复逻辑整段删掉这个用例照样绿</b>。它守不住任何东西。
 * 现在：key 用对的，断言收紧成「必须抛 NotLoginException」（未登录），
 * 并且明确要求业务方法一次都没被调用。
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
        // 1.46 的 SaReactorSyncHolder.setContext 走 SaStrategy 里的
        // createSaRequest/createSaResponse/createSaStorage —— 生产环境由 starter 的
        // SaTokenContextRegister 构造函数装上，单测不启动 Spring，得自己装一次。
        new SaTokenContextRegister();
    }

    @AfterEach
    void restore() {
        SaManager.setSaTokenContext(previousContext);
        SaReactorSyncHolder.clearContext();
    }

    @Test
    @DisplayName("调用线程没有上下文时，从 Reactor Context 恢复后再鉴权")
    void restoresContextFromReactorContext() {
        FakeController target = new FakeController();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new SaPermissionAspect());
        FakeController proxy = factory.getProxy();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/files/upload").build());

        // 在 boundedElastic 线程上调用代理方法：该线程上没有任何 Sa-Token ThreadLocal。
        // exchange 必须用 SaReactorHolder 自己的 key 写进 Reactor Context ——
        // 用 ServerWebExchange.class 是取不到的（那不是 Sa-Token 读的 key）。
        Mono<String> result = Mono.defer(() -> proxy.upload())
                .subscribeOn(Schedulers.boundedElastic())
                .contextWrite(ctx -> SaReactorHolder.setContext(ctx, exchange, e -> Mono.empty()));

        // 上下文恢复成功 → 鉴权真的跑起来了 → 因为没登录，抛的是 NotLoginException。
        // 如果恢复失败，这里会是 SaTokenContextException 或 NullPointerException。
        StepVerifier.create(result)
                .expectError(NotLoginException.class)
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
