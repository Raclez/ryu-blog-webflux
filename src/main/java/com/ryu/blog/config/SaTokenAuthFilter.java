package com.ryu.blog.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.spring.pathmatch.SaPathPatternParserUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.exception.GlobalExceptionHandler;
import com.ryu.blog.utils.OperatorContexts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录校验过滤器 —— 并把操作者身份放进 Reactor Context。
 *
 * <p><b>为什么不再继承官方的 {@code SaReactorFilter}</b>：
 * 官方那个把「鉴权失败」交给 {@code setError} 回调，回调必须<b>返回一段字符串</b>，
 * 由框架当纯文本写回（还不给状态码）—— 所以每个用官方过滤器的项目都得自己再写一遍
 * 渲染逻辑，结果就是<b>同一个 401/403 有两套 body</b>。
 *
 * <p>这里自己实现 WebFilter：鉴权失败直接把异常交给
 * {@link HandlerExceptionResolver} —— 也就是 {@code GlobalExceptionHandler}
 * （{@code @RestControllerAdvice}）—— 于是
 * <b>登录校验失败与能力校验失败、以及 Controller 里抛出的鉴权异常，
 * 出来的是同一种 body</b>，都走用户自己的全局异常处理。
 *
 * <h3>顺序</h3>
 * <pre>
 *   SaTokenContextFilterForReactor (-104)  框架：把 exchange 写进 Reactor Context
 *   SaTokenCorsFilterForReactor    (-103)  框架：CORS
 *   SaFirewallCheckFilterForReactor(-102)  框架：防火墙
 *   UserPermissionWarmupFilter     (-101)  本项目：权限快照预热
 *   SaTokenAuthFilter              (-100)  本项目：登录校验 + 采集身份
 *   PermissionCheckFilter          (-99)   本项目：能力校验（403 先于参数解析）
 * </pre>
 *
 * <h3>白名单</h3>
 * 基础设施路径（静态资源 / 接口文档 / 健康检查 / 认证接口）写死在这里；
 * 业务上的公开路径来自配置 {@code blog.auth.public-paths}。
 * 两者合起来就是「匿名可访问」的完整清单 ——
 * 回归护栏 {@code AuthPublicPathsTest}。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(-100)
public class SaTokenAuthFilter implements WebFilter {

    /**
     * 基础设施路径：静态资源、接口文档、健康检查、认证接口。
     *
     * <p>这些不是「业务上的公开接口」，写死在代码里；
     * 业务公开路径在配置 {@code blog.auth.public-paths} 里。
     */
    public static final List<String> INFRA_PATHS = List.of(
            "/favicon.ico",
            "/static/**",
            "/doc.html",
            "/webjars/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**",
            "/auth/login",
            "/auth/register",
            "/auth/captcha",
            "/auth/captcha/**",
            "/auth/check/**");

    private final AuthProperties authProperties;

    private final GlobalExceptionHandler globalExceptionHandler;

    public SaTokenAuthFilter(AuthProperties authProperties, GlobalExceptionHandler globalExceptionHandler) {
        this.authProperties = authProperties;
        this.globalExceptionHandler = globalExceptionHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isPublic(exchange)) {
            // 白名单路径：不做登录校验，但身份照样采集（公开接口也可能带 token）。
            return continueWithIdentity(exchange, chain);
        }

        SaReactorSyncHolder.setContext(exchange);
        try {
            StpUtil.checkLogin();
        } catch (Exception e) {
            logAuthFailure(e);
            SaReactorSyncHolder.clearContext();
            return deny(exchange, e);
        }
        SaReactorSyncHolder.clearContext();

        return continueWithIdentity(exchange, chain);
    }

    /**
     * 采集身份 → 写进 Reactor Context → 继续往下走。
     *
     * <p>身份必须在「请求线程 + Sa-Token 上下文在位」时采集 ——
     * 之后它就跟着 Reactor Context 走了，跨线程仍然读得到。
     */
    private Mono<Void> continueWithIdentity(ServerWebExchange exchange, WebFilterChain chain) {
        SaReactorSyncHolder.setContext(exchange);
        OperatorContext operator;
        try {
            operator = OperatorContexts.capture();
        } catch (Throwable e) {
            SaReactorSyncHolder.clearContext();
            throw e;
        }
        // <b>上下文要活到整条链结束</b>，不能在 capture 之后就清掉 ——
        // 控制器里有 30 多处直接 StpUtil.getLoginIdAsLong()，
        // 而切面只对<b>带鉴权注解</b>的方法恢复上下文；
        // 没有注解的端点（比如 /menus/my）依赖的就是这里建立的这份上下文。
        // 清早了 → 控制器方法体里抛 SaTokenContextException「SaTokenContext 尚未初始化」→ 500。
        return chain.filter(exchange)
                .contextWrite(context -> context.put(OperatorContexts.KEY, operator))
                .doFinally(signal -> SaReactorSyncHolder.clearContext());
    }

    /**
     * 本次请求是否匿名可达。
     */
    boolean isPublic(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        for (String pattern : publicPaths()) {
            // 注意参数顺序：SaPathPatternParserUtil.match(pattern, path)
            // —— 第一个参数被当成 PathPattern 解析，第二个才是被匹配的路径。
            if (SaPathPatternParserUtil.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 匿名可达的完整清单：基础设施路径 + 配置里的业务公开路径。
     */
    public List<String> publicPaths() {
        List<String> paths = new ArrayList<>(INFRA_PATHS);
        paths.addAll(authProperties.getPublicPaths());
        return paths;
    }

    /**
     * 鉴权失败的日志级别。
     *
     * <p>未登录是常态（token 过期、匿名扫描、爬虫），一律 ERROR 会把真正的异常淹没；
     * 权限不足是业务性的，INFO 就够；只有「说不清为什么失败」的才配得上告警。
     *
     * @param e 鉴权异常
     * @return 日志级别
     */
    static Level resolveAuthLogLevel(Throwable e) {
        if (e instanceof NotLoginException) {
            return Level.DEBUG;
        }
        if (e instanceof NotPermissionException || e instanceof NotRoleException) {
            return Level.INFO;
        }
        return Level.ERROR;
    }

    private void logAuthFailure(Throwable e) {
        Level level = resolveAuthLogLevel(e);
        if (level == Level.ERROR) {
            log.error("Sa-Token鉴权异常: {}", e.getMessage(), e);
        } else if (level == Level.INFO) {
            log.info("Sa-Token鉴权失败: {}", e.getMessage());
        } else {
            log.debug("Sa-Token鉴权失败: {}", e.getMessage());
        }
    }

    /**
     * 拒绝：交给全局异常处理渲染。
     *
     * <p>不能直接 {@code throw} —— WebFilter 在 {@code DispatcherHandler} 之外，
     * 抛出去的异常到不了 {@code @RestControllerAdvice}，
     * 会被 Boot 的兜底错误处理器渲染成另一种 body。
     */
    private Mono<Void> deny(ServerWebExchange exchange, Throwable e) {
        if (!(e instanceof NotLoginException failure)) {
            // <b>不是「没登录」就别装成没登录</b>：Redis 挂了、Sa-Token 上下文没建立起来，
            // 这些都是基础设施故障。报成 401 会让客户端以为 token 过期、丢掉有效登录态，
            // 监控看到的也是「401 突增」而不是「Redis 挂了」。
            // 这里让它继续往上抛，由框架的兜底错误处理器渲染成 500。
            log.error("登录校验失败（不是未登录，属于故障）: {}", e.getMessage(), e);
            return Mono.error(e);
        }
        return globalExceptionHandler.handleNotLoginException(failure, exchange)
                .flatMap(result -> GlobalExceptionHandler.writeAuthFailure(exchange, HttpStatus.UNAUTHORIZED, result))
                .then();
    }
}
