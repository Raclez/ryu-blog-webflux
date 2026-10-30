package com.ryu.blog.service.impl;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.exception.PermissionDeniedException;

import java.util.Objects;

/**
 * 归属判定规则
 *
 * <p>与 {@code ArticleVisibilityPolicy} 同构：<b>纯函数、无 Spring 依赖、可直接单测</b>。
 * 之所以要收敛成一处，是因为这条规则一旦散落到各个 Service 里，
 * 就会出现「改一处漏一处 → 某个端点忘了归属校验 → 越权」的静默漏洞。
 *
 * <p><b>规则只有一条</b>：操作者要么是资源的主人，要么拥有「管理他人」的能力。
 *
 * <pre>{@code
 * ownerId == null            → 允许（资源没有归属概念，例如角色、菜单）
 * operatorId == ownerId      → 允许（本人操作）
 * canManageOthers()          → 允许（管理员）
 * 否则                        → 拒绝
 * }</pre>
 *
 * <p>「改自己」与「改别人」用两个<b>正交</b>的能力表达，而不是为每个动作发明
 * {@code :any} 变体 identity：
 * <ul>
 *   <li>{@code system:user:edit} —— 编辑自己的资料（默认角色就有）</li>
 *   <li>{@code system:user:manage} —— 管理他人账户（仅管理员）</li>
 * </ul>
 * 这样新增端点时只需回答「这是不是本人操作」，判定式永远只有一行。
 *
 * @author ryu
 * @since 1.0.0
 */
public final class OwnershipPolicy {

    private OwnershipPolicy() {
    }

    /**
     * 当前操作者能否对该资源执行操作
     *
     * @param ctx     操作者上下文
     * @param ownerId 资源归属人ID（无归属概念的资源传 null）
     * @return 允许返回 true
     */
    public static boolean canActOn(OperatorContext ctx, Long ownerId) {
        if (ctx == null) {
            return false;
        }
        // 资源没有归属概念
        if (ownerId == null) {
            return true;
        }
        // 本人
        if (Objects.equals(ctx.userId(), ownerId)) {
            return true;
        }
        // 管理员
        return ctx.canManageOthers();
    }

    /**
     * 当前操作者是否是资源的主人（不区分管理员）
     *
     * @param ctx     操作者上下文
     * @param ownerId 资源归属人ID
     * @return 是本人返回 true
     */
    public static boolean isSelf(OperatorContext ctx, Long ownerId) {
        return ctx != null && ownerId != null && Objects.equals(ctx.userId(), ownerId);
    }

    /**
     * 断言当前操作者能否对该资源执行操作，否则抛 403
     *
     * @param ctx      操作者上下文
     * @param ownerId  资源归属人ID（无归属概念的资源传 null）
     * @throws PermissionDeniedException 无权操作时抛出
     */
    public static void requireCanActOn(OperatorContext ctx, Long ownerId) {
        if (!canActOn(ctx, ownerId)) {
            throw PermissionDeniedException.accessDenied("无权操作他人的数据");
        }
    }

    /**
     * 断言当前操作者<b>只能</b>操作自己的资源（连管理员也不行）。
     *
     * <p>用于「改自己的密码」「查自己的权限」这类自助接口 ——
     * 即便调用方是管理员，也只能操作自己。
     *
     * @param ctx     操作者上下文
     * @param ownerId 资源归属人ID
     * @throws PermissionDeniedException 不是本人时抛出
     */
    public static void requireIsSelf(OperatorContext ctx, Long ownerId) {
        if (!isSelf(ctx, ownerId)) {
            throw PermissionDeniedException.accessDenied("只能操作自己的数据");
        }
    }
}
