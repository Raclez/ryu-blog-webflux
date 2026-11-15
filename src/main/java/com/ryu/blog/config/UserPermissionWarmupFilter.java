package com.ryu.blog.config;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.service.impl.UserPermissionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *   SaTokenContextFilterForReactor (-104) → 建立 Sa-Token 上下文
 *   SaTokenCorsFilterForReactor    (-103) → CORS / 预检
 *   SaFirewallCheckFilterForReactor(-102) → 防火墙
 *   UserPermissionWarmupFilter     (-101) → 反应式解析权限 → 写入内存快照
 *   SaTokenAuthFilter              (-100) → 登录校验 → 采集身份
 *   Controller 上的 @SaCheckPermission → StpInterface 读内存快照（零阻塞）
 * </pre>
 *
 * <p><b>顺序是显式定的，不是碰巧对的。</b>
 * 早先这个过滤器挂的是 {@code Ordered.HIGHEST_PRECEDENCE}，排在框架的上下文过滤器
 * （-104）<b>之前</b>，于是它必须自己 {@code SaReactorSyncHolder.setContext(exchange)}
 * 才能读到 token —— 而那个上下文<b>建完就不管了</b>，在池化线程上留一个 box。
 * 「当前线程上有没有 box」从此失去意义（上一个请求的 box 也算「有」），
 * 这正是 {@code SaPermissionAspect} 曾经用错身份的根因。
 * 现在它排在 -101，落在框架上下文过滤器之后，并且<b>只在用它的那个作用域里存在</b>。
 *
 * <p>回归护栏：{@code UserPermissionWarmupFilterNoLeakTest}（不得留下残留上下文）、
 * {@code UserPermissionWarmupFilterContextTest}（异步刷新后要能继续往下走）。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(-101)
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
        return permissionCache.ensureWarm(userId)
                .onErrorResume(e -> {
                    log.warn("预热用户权限快照失败，本次请求将按无权限处理: userId={}, 原因: {}",
                            userId, e.getMessage(), e);
                    return Mono.empty();
                })
                // refresh 走 R2DBC / Redis，续作是在<b>另一个线程</b>上恢复的。
                // 这里<b>刻意不再</b>重建 Sa-Token 的 ThreadLocal 上下文：
                // 往下走的 SaTokenAuthFilter 会为整条链建立上下文（它在 -100，排在预热之后），
                // 而在这里重建的那一份<b>永远不会被清掉</b> ——
                // 留在池化线程上，下一个请求跑在这个线程上时就会读到「上一个请求的 exchange」。
                .then(Mono.defer(() -> chain.filter(exchange)));
    }

    /**
     * 从请求里解析出登录用户ID
     *
     * <p>这里要自己建立 Sa-Token 的 Reactor 上下文：本过滤器排在
     * {@code SaTokenAuthFilter} 之前，不能指望上下文已经就绪。
     *
     * <p><b>建立的上下文必须在用完之后清掉</b> —— 线程是池化的，不清就会把这次请求的
     * exchange 留给下一个跑在这个线程上的请求。回归护栏：
     * {@code UserPermissionWarmupFilterNoLeakTest}、{@code UserPermissionWarmupFilterContextTest}。
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
        } finally {
            // 只在解析 token 的那一下需要上下文；用完立刻清掉，
            // 不给「下一个请求跑在这个线程上」留下歧义。
            SaReactorSyncHolder.clearContext();
        }
    }
}
