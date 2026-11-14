package com.ryu.blog.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.utils.JsonUtils;
import com.ryu.blog.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ServerWebExchange;



/**
 * Sa-Token配置类
 *
 * <p><b>鉴权分层</b>（整改后的分工）：
 * <ol>
 *   <li><b>登录校验</b> —— 本类的 {@code SaReactorFilter}，白名单按<b>真实路由</b>列；</li>
 *   <li><b>能力校验</b> —— Controller 上的 {@code @SaCheckPermission}，
 *       由 {@link SaPermissionAspect} 执行（WebFlux 下 Sa-Token 自身不提供注解拦截器）；</li>
 *   <li><b>归属校验</b> —— Service 层的 {@code OwnershipPolicy}（本人 or 管理他人）。</li>
 * </ol>
 *
 * <p>原先这里有一条 {@code SaRouter.match("/admin/**").check(r -> StpUtil.checkRole("admin"))}，
 * 但<b>全项目没有任何控制器挂在 {@code /admin/} 下</b>（连 {@code controller/admin/}
 * 包下的 CacheController 真实路径也是 {@code /cache}）→ 这条规则命中不到任何路由，
 * 形同虚设；再加上没有 {@code StpInterface}、角色码大小写又不符（代码用小写 "admin"，
 * 库里是大写 'ADMIN'），它即便命中也会对所有人抛 {@code NotRoleException}。
 * 现在这条死规则已删除，权限统一由注解表达。
 *
 * <p><b>鉴权失败的响应</b>：{@code SaReactorFilter} 自己不会设置 HTTP 状态码，
 * 而且把 {@code setError} 的返回值 {@code String.valueOf} 之后当纯文本写回。
 * 所以这里自己渲染成「正确的状态码（401/403）+ JSON 响应体」，
 * 详见 {@link #renderAuthError(Throwable)}。
 *
 * @author ryu
 */
@Slf4j
@Configuration
public class SaTokenConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 使用JWT无状态登录
     */
    @Bean
    public StpLogic stpLogic() {
        return new StpLogicJwtForMixin();
    }

    /**
     * 注册Sa-Token全局过滤器
     *
     * <p><b>顺序是显式钉死的</b>：-100，落在框架的三个过滤器之后
     * （{@code SaTokenContextFilterForReactor} -104 → 上下文，
     * {@code SaTokenCorsFilterForReactor} -103 → CORS，
     * {@code SaFirewallCheckFilterForReactor} -102 → 防火墙），
     * 以及本项目的 {@code UserPermissionWarmupFilter}（-101 → 权限快照预热）之后。
     *
     * <p>预热必须排在鉴权<b>之前</b>：{@code SaTokenAuthFilter} 在下游采集操作者身份时
     * 会读权限快照（{@code canManageOthers}），快照没就绪的话那个标记会恒为 false。
     */
    @Bean
    @Order(-100)
    public SaReactorFilter saReactorFilter() {
        // 用 SaTokenAuthFilter 而不是官方的 SaReactorFilter：
        // 1.42 的官方实现鉴权完就不再重建 ThreadLocal 上下文（见 SaTokenAuthFilter 的注释），
        // 下游 Controller/Service 里的 StpUtil.getLoginIdAsLong() 会直接 500。
        return new SaTokenAuthFilter()
                // 拦截地址
                .addInclude("/**")
                // 排除地址
                .addExclude(
                        // 静态资源
                        "/favicon.ico",
                        "/static/**",

                        // API文档资源
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",

                        // 健康检查 —— 探针/容器编排要匿名访问，
                        // 不放行的话 /actuator/health 会 401，健康检查直接失联。
                        // 只放行 health，其余 actuator 端点（env/heapdump…）
                        // 本来就没有配置 exposure.include，不会对外暴露。
                        "/actuator/health",
                        "/actuator/health/**",

                        // 认证相关（公开）
                        "/auth/login",
                        "/auth/register",
                        "/auth/captcha",
                        "/auth/captcha/**",
                        "/auth/check/**",

                        // 前台博客的公开只读接口。
                        // 原先的白名单写的是 /content/article/list、/content/article/detail/**
                        // 这类**并不存在的路由**，而真实存在的 /posts/detail/{id}、
                        // /posts/related/{postId}/{limit} 反而没被放行 ——
                        // 结果是公开博客的文章正文匿名访问会 401。
                        "/posts/front",
                        "/posts/detail/**",
                        "/posts/related/**",
                        "/category/all",
                        "/category/page",
                        "/tags/list",
                        "/tags/hot",
                        "/tags/page",
                        "/comment/article/**",

                        // 公开配置（站点名称/logo/展示参数）。
                        // 登录页在拿不到 token 时也要渲染品牌信息，所以必须匿名可达；
                        // 只返回 PublicConfigKeys 白名单内的键，AI/安全/存储类配置
                        // 仍然待在 /sysConfig/** 的管理员权限之后。
                        "/sysConfig/public",

                        // 公开文件直链。
                        // 匿名可访问 —— <img src> 带不了 Authorization 头，
                        // 按 ID 的预览路由每次访问都要 system:file:view 权限 + 请求头，
                        // 博客正文里的图根本渲染不出来。
                        // 文件可见性已下线（桶本身就是匿名可读的），这条路由不再做权限判定。
                        "/files/public/**"
                )
                // 鉴权方法：每次访问进入
                // 白名单已经由上面的 addExclude 决定 —— 过滤器内部是
                //   SaRouter.match(includeList).notMatch(excludeList).check(auth)
                // 也就是说 check 只会作用在「命中 include 且不在 exclude」的路径上。
                // 原先这里还套了一层 SaRouter.match("/**").notMatch(...)，
                // 里面列的 /content/article/list、/user/login、/ip/info ……
                // 全是**并不存在的路由**，而真实存在的公开路由已经在 addExclude 里。
                // 那层嵌套除了重复之外，还会让人误以为改那一处就能改白名单。
                // 现在白名单只有一个出处：addExclude。
                .setAuth(obj -> {
                    try {
                        StpUtil.checkLogin();
                    } catch (Exception e) {
                        log.debug("登录校验失败: {}", e.getMessage());
                        throw e;
                    }
                })
                // 【已删除】原先这里有一条：
                //   SaRouter.match("/admin/**").check(r -> StpUtil.checkRole("admin"));
                // 它匹配不到任何路由（没有任何控制器在 /admin/ 下），
                // 且角色码大小写与库里的 'ADMIN' 不符 —— 纯死规则。
                // 权限校验现在由 @SaCheckPermission 注解承担。
                //
                // 异常处理
                .setError(e -> {
                    // 必须把异常对象本身传进去：只打 getMessage() 会把异常类型和堆栈全部丢掉，
                    // 线上看到「token无效」根本分不清是过期、伪造还是签名不匹配。
                    log.error("Sa-Token鉴权异常: {}", e.getMessage(), e);
                    return renderAuthError(e);
                });
    }

    /**
     * 把 Sa-Token 的鉴权异常渲染成「正确的 HTTP 状态码 + JSON 响应体」
     *
     * <p><b>为什么必须自己渲染</b>（反编译 {@code SaReactorFilter.filter} 确认）：
     * 过滤器在 catch 到异常后做的是
     * <pre>
     *   Object result = error.run(e);
     *   String.valueOf(result)   ← 直接 toString！
     *   response.writeWith(...)  ← 纯文本，且**从不调用 setStatusCode**
     * </pre>
     * 也就是说：原先返回 {@code Result.error(msg)} 的写法，实际吐给客户端的是
     * <ul>
     *   <li>HTTP 状态码 <b>200</b>（不是 401/403）；</li>
     *   <li>Content-Type {@code text/plain}；</li>
     *   <li>响应体是 Lombok 的 {@code toString()} —— {@code Result(code=500, message=..., data=null)}，
     *       <b>不是 JSON</b>，前端 {@code JSON.parse} 直接炸。</li>
     * </ul>
     * 所以这里自己补状态码、Content-Type 和 JSON 序列化。
     *
     * @param e 鉴权异常
     * @return JSON 字符串（过滤器会把它原样写入响应体）
     */
    private String renderAuthError(Throwable e) {
        HttpStatus status = resolveAuthStatus(e);
        Result<?> result = resolveAuthResult(e, status);
        applyAuthErrorResponse(status);
        return JsonUtils.toJsonString(result);
    }

    /**
     * 判定鉴权异常对应的 HTTP 状态码
     */
    private HttpStatus resolveAuthStatus(Throwable e) {
        if (e instanceof NotLoginException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (e instanceof NotPermissionException || e instanceof NotRoleException) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 构造鉴权异常对应的响应体
     */
    private Result<?> resolveAuthResult(Throwable e, HttpStatus status) {
        String message = e.getMessage() == null ? status.getReasonPhrase() : e.getMessage();
        return switch (status) {
            case UNAUTHORIZED -> Result.unauthorized(message);
            case FORBIDDEN -> Result.forbidden(message);
            default -> Result.error(status.value(), message);
        };
    }

    /**
     * 把状态码和 Content-Type 写进响应
     *
     * <p>过滤器写响应体之前不会 commit，所以这里设置还来得及。
     * 拿不到 exchange 时（比如上下文已经被清掉）就退化成「只改 body」——
     * 状态码会退化成 200，但至少响应体是合法 JSON。
     */
    private void applyAuthErrorResponse(HttpStatus status) {
        ServerWebExchange exchange = currentExchange();
        if (exchange == null || exchange.getResponse().isCommitted()) {
            return;
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    }

    /**
     * 取当前请求对应的 exchange
     *
     * <p>异常处理器运行时 Sa-Token 的上下文还在（过滤器是在 {@code writeWith}
     * 之后才清理），所以这里能拿到；拿不到就返回 null。
     */
    private ServerWebExchange currentExchange() {
        try {
            return SaReactorSyncHolder.getExchange();
        } catch (Exception e) {
            return null;
        }
    }
}
