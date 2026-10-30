package com.ryu.blog.config;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.service.impl.UserPermissionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 用户权限快照预热过滤器
 *
 * <p>存在的唯一理由：<b>让 {@code @SaCheckPermission} 的鉴权有数据可查</b>。
 *
 * <p>Sa-Token 的 {@code StpInterface} 是同步接口，鉴权发生在 Controller 方法上的
 * AOP 拦截器里 —— 那时已经不能做响应式查询了。所以权限快照必须在进入 Controller
 * <b>之前</b>准备好，也就是 WebFilter 阶段。
 *
 * <p>顺序保证：
 * <pre>
 *   SaReactorFilter            → 建立 Sa-Token 上下文
 *   UserPermissionWarmupFilter → 反应式解析权限 → 写入内存快照
 *   Controller 上的 @SaCheckPermission → StpInterface 读内存快照（零阻塞）
 * </pre>
 *
 * <p>本过滤器只做「快照未就绪时才查库」，命中缓存时直接放行，不产生额外开销。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserPermissionWarmupFilter implements WebFilter {

    private final UserPermissionCache permissionCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Long userId = resolveLoginId(exchange);
        if (userId == null || permissionCache.isWarm(userId)) {
            return chain.filter(exchange);
        }
        // 解析失败也不能阻断请求 —— 鉴权侧会 fail-closed 返回 403，而不是 500。
        // 但必须留下日志：否则「权限快照拉取失败」在日志里完全看不见，
        // 现场只看到一堆莫名其妙的 403，无从判断是权限确实不足还是快照没就绪。
        return permissionCache.refresh(userId)
                .onErrorResume(e -> {
                    log.warn("预热用户权限快照失败，本次请求将按无权限处理: userId={}, 原因: {}",
                            userId, e.getMessage(), e);
                    return Mono.empty();
                })
                // refresh 走 R2DBC / Redis，续作是在<b>另一个线程</b>上恢复的；
                // 而 Sa-Token 的响应式上下文是 ThreadLocal（SaReactorSyncHolder），
                // 不会跟着线程切换走。不在这里重建，进入 Controller 时
                // @SaCheckPermission 直接抛 SaTokenContextException「未能获取有效的上下文」。
                // 现象很误导：快照命中时一切正常，快照刚过期（TTL 60 秒）的那次请求必挂。
                .then(Mono.fromRunnable(() -> SaReactorSyncHolder.setContext(exchange)))
                .then(Mono.defer(() -> chain.filter(exchange)));
    }

    /**
     * 从请求里解析出登录用户ID
     *
     * <p>这里要自己建立 Sa-Token 的 Reactor 上下文：本过滤器排在
     * {@code SaReactorFilter} 之前，不能指望上下文已经就绪。
     * 建立的上下文不清除 —— {@code SaReactorFilter} 会在整条链结束时统一清理。
     *
     * <p>包级可见（不是 private）：{@code UserPermissionWarmupFilterContextTest} 靠覆写它
     * 跳过「真的登录一个用户」，把用例聚焦在上下文重建上。
     *
     * @param exchange 服务器交换器
     * @return 用户ID；未登录或 token 无效时返回 null
     */
    Long resolveLoginId(ServerWebExchange exchange) {
        try {
            SaReactorSyncHolder.setContext(exchange);
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            if (loginId instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(loginId.toString());
        } catch (Exception e) {
            // token 无效 / 过期 / 格式错误 —— 交给后续的 checkLogin 处理。
            // 这里只记 debug：匿名访问与过期 token 是常态，不必刷 warn。
            log.debug("解析登录用户失败，按未登录处理: {}", e.getMessage());
            return null;
        }
    }
}
