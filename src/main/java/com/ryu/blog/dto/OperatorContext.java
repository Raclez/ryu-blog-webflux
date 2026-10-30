package com.ryu.blog.dto;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.constant.PermissionConstants;

/**
 * 操作者上下文
 *
 * <p>承载「这次操作是谁在做」以及「他能不能管别人」这两个信息，
 * 由 Controller 装配后<b>显式传给 Service</b>。
 *
 * <p><b>为什么不在 Service 里隐式取 {@code StpUtil.getLoginIdAsLong()}：</b>
 * <ul>
 *   <li>隐式取会把 Service 绑死 Web 安全上下文 —— 单元测试要 mock 全局静态，
 *       非 HTTP 调用（任务处理器、事件监听器）根本拿不到操作者；</li>
 *   <li>显式传参让 Service 保持上下文无关、可直接单测，
 *       也保证「谁在做」这件事在调用链上是可见的。</li>
 * </ul>
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
     * <p>必须在 <b>Controller 方法体内</b>调用（与项目里既有的
     * {@code StpUtil.getLoginIdAsLong()} 用法一致）—— Sa-Token 的 Reactor 上下文
     * 靠 {@code SaReactorSyncHolder} 随链传播，链内取可能已经拿不到。
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
