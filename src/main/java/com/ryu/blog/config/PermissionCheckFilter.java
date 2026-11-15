package com.ryu.blog.config;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.exception.NotPermissionException;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.exception.GlobalExceptionHandler;
import com.ryu.blog.service.impl.PermissionSnapshot;
import com.ryu.blog.service.impl.UserPermissionCache;
import com.ryu.blog.utils.OperatorContexts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 能力校验过滤器 —— <b>在建 handler 之前</b>就判权限。
 *
 * <p><b>它守的是什么</b>：能力校验原先挂在 Controller 方法上的 AOP 切面里，
 * 而 WebFlux 的参数解析（{@code @RequestBody} + {@code @Validated}）
 * 发生在<b>方法被调用之前</b> —— 也就是在切面之前。
 *
 * <p>于是未授权用户带着非法请求体调写接口，拿到的是 <b>400</b> 而不是 <b>403</b>：
 * 顺带把「哪些字段必填、什么格式」告诉了不该知道的人；而且未授权请求
 * 照样触发完整的反序列化与校验 —— 白干活。
 *
 * <p>这里在过滤器阶段只解析<b>路径</b>（{@code getHandler(exchange)} 不碰请求体），
 * 于是 403 一定先于 400。
 *
 * <p><b>顺序</b>：-99，排在 {@code UserPermissionWarmupFilter}(-101，预热权限快照)
 * 与 {@code SaTokenAuthFilter}(-100，登录校验) 之后 ——
 * 所以拿得到「当前是谁」，也拿得到快照。
 *
 * <p><b>只处理它能完全判定的注解</b>：
 * {@code @SaCheckPermission}（AND / OR，不带 {@code orRole}）。
 * 遇到 {@code orRole}、{@code @SaCheckRole}、{@code @SaCheckLogin}
 * 一律<b>放行给切面</b>去判 —— 那边实现的语义更全，
 * 两边各判各的不会漏（切面仍然是兜底）。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(-99)
public class PermissionCheckFilter implements WebFilter {

    private final UserPermissionCache permissionCache;

    /**
     * 全局异常处理器。
     *
     * <p>鉴权失败<b>交给它渲染</b> —— 与 Controller 里抛出的异常走同一套，
     * 前端只面对一种 body。
     */
    private final GlobalExceptionHandler globalExceptionHandler;

    private final RequestMappingHandlerMapping handlerMapping;

    /**
     * 手工写构造器而不是用 Lombok：{@code @Qualifier} 必须落在构造参数上，
     * Lombok 默认不复制它（要 copyableAnnotations 配置），那样注入会找错 bean。
     */
    public PermissionCheckFilter(UserPermissionCache permissionCache,
                                 GlobalExceptionHandler globalExceptionHandler,
                                 @Qualifier("requestMappingHandlerMapping")
                                 RequestMappingHandlerMapping handlerMapping) {
        this.permissionCache = permissionCache;
        this.globalExceptionHandler = globalExceptionHandler;
        this.handlerMapping = handlerMapping;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // <b>不能写成 .then(chain.filter(exchange))</b> ——
        // chain.filter(exchange) 是 then() 的<b>实参</b>，会被<b>提前求值</b>：
        // 判定还没跑，下游就已经被调用了。于是「写了 403 之后请求仍继续往下走」，
        // 业务逻辑照常执行（响应写不进去而已，但副作用已经发生）。
        //
        // 所以必须先拿到「放行 / 拒绝」的结论，再决定要不要走下游。
        return resolveHandler(exchange)
                .flatMap(handler -> verify(exchange, handler))
                // 没有 handler、没有能力要求 —— 放行
                .defaultIfEmpty(Boolean.TRUE)
                .flatMap(granted -> granted ? chain.filter(exchange) : Mono.empty());
    }

    /**
     * 解析出本次请求对应的 handler 方法。
     *
     * <p>解析不出来（404、静态资源、非 Controller 的端点）就当没有要求。
     */
    private Mono<HandlerMethod> resolveHandler(ServerWebExchange exchange) {
        return handlerMapping.getHandler(exchange)
                .ofType(HandlerMethod.class)
                .onErrorResume(e -> {
                    // 用 WARN 而不是 DEBUG：解析不出 handler 意味着这个端点<b>没有</b>被
                    // 能力校验覆盖，只能靠切面兜底 —— 这件事值得被看见。
                    log.warn("解析 handler 失败，本次跳过能力校验（仅靠切面兜底）: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 判定这个端点要不要权限，以及当前操作者有没有。
     */
    private Mono<Boolean> verify(ServerWebExchange exchange, HandlerMethod handler) {
        Class<?> beanType = handler.getBeanType();
        java.lang.reflect.Method method = handler.getMethod();

        if (AnnotatedElementUtils.hasAnnotation(method, SaIgnore.class)
                || AnnotatedElementUtils.hasAnnotation(beanType, SaIgnore.class)) {
            return Mono.just(Boolean.TRUE);
        }

        SaCheckPermission annotation = AnnotatedElementUtils.findMergedAnnotation(method, SaCheckPermission.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(beanType, SaCheckPermission.class);
        }
        if (annotation == null) {
            // 没有能力要求 —— 交给后面（登录校验仍然生效）。
            return Mono.just(Boolean.TRUE);
        }

        String[] required = annotation.value();
        if (required == null || required.length == 0) {
            return Mono.just(Boolean.TRUE);
        }
        String[] orRoles = annotation.orRole();
        if (orRoles != null && orRoles.length > 0) {
            // orRole 的语义（权限或角色任一满足）由切面实现，这边不重复判断。
            return Mono.just(Boolean.TRUE);
        }

        boolean and = annotation.mode() != cn.dev33.satoken.annotation.SaMode.OR;

        return Mono.deferContextual(cv -> {
            OperatorContext operator = OperatorContexts.from(cv);
            if (operator.userId() == null) {
                // 没登录 —— 但登录校验在前面的过滤器里已经处理过了，
                // 走到这里说明是白名单路径，那就按「无权限」处理更安全。
                return deny(exchange, handler, required[0]);
            }
            PermissionSnapshot snapshot = permissionCache.getSnapshot(operator.userId());
            Set<String> owned = snapshot == null ? Set.of() : snapshot.permissions();
            boolean granted = and
                    ? java.util.Arrays.stream(required).allMatch(owned::contains)
                    : java.util.Arrays.stream(required).anyMatch(owned::contains);
            if (!granted) {
                return deny(exchange, handler, required[0]);
            }
            return Mono.just(Boolean.TRUE);
        });
    }

    /**
     * 拒绝：<b>委托给全局异常处理</b>渲染。
     *
     * <p>{@code HandlerExceptionResolver} 就是 {@code DispatcherHandler} 内部用的那一套
     * —— 里面装着 {@code GlobalExceptionHandler}（{@code @RestControllerAdvice}）。
     * 所以过滤器里的鉴权失败和 Controller 里抛出的鉴权失败，出来的是<b>同一种 body</b>。
     *
     * <p>注意不能直接 {@code throw}：WebFilter 在 {@code DispatcherHandler} 之外，
     * 抛出去的异常到不了 {@code @RestControllerAdvice}，
     * 会被 Boot 的兜底错误处理器渲染成另一种 body。
     */
    private Mono<Boolean> deny(ServerWebExchange exchange, HandlerMethod handler, String identity) {
        return globalExceptionHandler
                .handleNotPermissionException(new NotPermissionException(identity), exchange)
                .flatMap(result -> GlobalExceptionHandler.writeAuthFailure(exchange, HttpStatus.FORBIDDEN, result))
                .thenReturn(Boolean.FALSE);
    }
}
