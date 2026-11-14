package com.ryu.blog.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import com.ryu.blog.utils.JsonUtils;
import com.ryu.blog.utils.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 鉴权失败的响应渲染。
 *
 * <p><b>为什么单独抽出来</b>：鉴权失败有两条路径 ——
 * 过滤器（登录校验、{@code PermissionCheckFilter} 的能力校验）
 * 与 AOP 切面（能力校验兜底）。两条路径必须给出<b>同一种</b>响应，
 * 否则前端要处理两种 body。而框架的 {@code SaReactorFilter} 自己不会设置状态码，
 * 也不管 Content-Type，所以这块必须自己写。
 *
 * @author ryu
 * @since 1.0.0
 */
@Component
public class AuthErrorRenderer {

    /**
     * 把异常渲染成「正确的 HTTP 状态码 + JSON 响应体」，并写进响应。
     *
     * @param exchange 当前请求
     * @param e        鉴权异常
     * @return 写完响应体的 Mono
     */
    public Mono<Void> write(ServerWebExchange exchange, Throwable e) {
        HttpStatus status = resolveStatus(e);
        Result<?> result = resolveResult(e, status);
        if (!exchange.getResponse().isCommitted()) {
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        }
        byte[] body = JsonUtils.toJsonString(result).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    /**
     * 判定鉴权异常对应的 HTTP 状态码
     */
    public HttpStatus resolveStatus(Throwable e) {
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
    public Result<?> resolveResult(Throwable e, HttpStatus status) {
        // <b>固定文案</b>，不用异常自带的 message：
        // 异常 message 里常带着 identity / token 细节，那是给日志看的，不该回给调用方；
        // 而且两条鉴权路径（过滤器 / 切面）必须给出同一种 body，前端才不用写两套解析。
        return switch (status) {
            case UNAUTHORIZED -> Result.unauthorized("未登录");
            case FORBIDDEN -> Result.forbidden("权限不足");
            default -> Result.error(status.value(), status.getReasonPhrase());
        };
    }

    /**
     * 取当前请求对应的 exchange（从 Sa-Token 的 ThreadLocal 上下文里）。
     *
     * <p>异常处理器运行时 Sa-Token 的上下文还在（过滤器是在 {@code writeWith}
     * 之后才清理），所以这里能拿到；拿不到就返回 null。
     */
    public ServerWebExchange currentExchange() {
        try {
            return SaReactorSyncHolder.getExchange();
        } catch (Exception e) {
            return null;
        }
    }
}
