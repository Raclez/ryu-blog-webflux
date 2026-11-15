package com.ryu.blog.dto;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.constant.PermissionConstants;

/**
 * 操作者上下文
 *
 * <p>承载「这次操作是谁在做」以及「他能不能管别人」这两个信息。
 *
 * <p><b>它现在不再作为方法参数传递</b>：由 {@code SaTokenAuthFilter} 在请求线程上
 * 采集一次，写进 <b>Reactor Context</b>；Service 层用
 * {@code Mono.deferContextual(...)} 取（见 {@code OperatorContexts}）。
 *
 * <p><b>为什么用 Reactor Context 而不是 ThreadLocal：</b>
 * Sa-Token 的上下文是纯 ThreadLocal，响应式链上一换线程就丢
 * （{@code SaTokenContextException}）；Reactor Context 随数据流走，跨线程仍在。
 *
 * <p><b>为什么不在 Service 里隐式取 {@code StpUtil.getLoginIdAsLong()}：</b>
 * 同上 —— 响应式链上换线程就取不到；而且那样会把 Service 绑死 Web 安全上下文，
 * 非 HTTP 调用（任务处理器、事件监听器）根本拿不到操作者。
 *
 * <p>归属规则本身收敛在 {@code OwnershipPolicy}（纯函数），这里只负责携带数据。
 *
 * @param userId          当前操作者用户ID（未登录为 null）
 * @param canManageOthers 是否拥有「管理他人账户」的能力（{@code system:user:manage}）
 * @author ryu
 * @since 1.0.0
 */
public record OperatorContext(Long userId, boolean canManageOthers) {

    /**
     * 匿名操作者 —— 未登录或上下文缺失时使用
     */
    public static final OperatorContext ANONYMOUS = new OperatorContext(null, false);

    /**
     * 从当前登录态装配。
     *
     * <p>必须在 <b>请求线程上、Sa-Token 的上下文还在时</b>调用 ——
     * 写入点见 {@code SaTokenAuthFilter}。
     *
     * @return 当前操作者上下文；未登录时返回 {@link #ANONYMOUS}
     */
    public static OperatorContext current() {
        try {
            if (!StpUtil.isLogin()) {
                return ANONYMOUS;
            }
            Long userId = StpUtil.getLoginIdAsLong();
            boolean canManageOthers = StpUtil.hasPermission(PermissionConstants.USER_MANAGE);
            return new OperatorContext(userId, canManageOthers);
        } catch (Exception e) {
            // 取不到登录态时按匿名处理，由上层的权限校验兜底
            return ANONYMOUS;
        }
    }

    /**
     * 是否是已登录的操作者
     */
    public boolean isAuthenticated() {
        return userId != null;
    }
}
