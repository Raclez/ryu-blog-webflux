package com.ryu.blog.service.impl;

import com.ryu.blog.dto.OperatorContext;

/**
 * 角色变更判定规则
 *
 * <p>与 {@link OwnershipPolicy} 同构：<b>纯函数、无 Spring 依赖、可直接单测</b>。
 * 之所以独立成一处，是因为「改资料」和「改角色」是<b>两件事</b>，
 * 而 {@code PUT /users/edit/{id}} / {@code POST /users/save} 两个端点
 * 都会在同一个请求体里同时带这两种意图。
 *
 * <p><b>规则只有一条</b>：分配角色是独立能力（{@code system:user:assign}），
 * 不能被「能改别人资料」顺带获得。
 *
 * <pre>{@code
 * 改自己            → IGNORED（请求体里的 roleIds 一律忽略，否则就是自己给自己升权）
 * 改别人但角色没变   → UNCHANGED（不算「改角色」，不额外要权限）
 * 改别人且角色变了   → NEEDS_ASSIGN_PERMISSION（要 system:user:assign）
 * }</pre>
 *
 * <p>持有 {@code system:user:assign} 与否不在这里判定 ——
 * 那是「有没有某项能力」的问题，交给 {@link UserPermissionResolver}，
 * 本类只回答「这次操作算不算改角色」。
 *
 * @author ryu
 * @since 1.0.0
 */
public final class RoleChangePolicy {

    private RoleChangePolicy() {
    }

    /**
     * 角色变更的判定结果
     */
    public enum Decision {
        /** 改自己：请求体里的 roleIds 一律忽略 */
        IGNORED,
        /** 角色集合没变：不涉及「改角色」 */
        UNCHANGED,
        /** 改别人的角色：需要 system:user:assign */
        NEEDS_ASSIGN_PERMISSION
    }

    /**
     * 判定一次角色变更该如何处理
     *
     * @param ctx            操作者上下文
     * @param targetUserId   被改角色的用户ID
     * @param roleSetChanged 请求的角色集合与当前是否不同
     * @return 判定结果
     */
    public static Decision decide(OperatorContext ctx, Long targetUserId, boolean roleSetChanged) {
        if (ctx == null) {
            return Decision.NEEDS_ASSIGN_PERMISSION;
        }
        // 改自己：忽略。不能自己给自己升权 ——
        // 否则 system:user:edit 就等于「随便给自己一个 admin 角色」。
        if (OwnershipPolicy.isSelf(ctx, targetUserId)) {
            return Decision.IGNORED;
        }
        // 角色没动：只是改资料，不该额外要权限
        // （前端编辑弹窗总会带上当前角色，没动它就不该把人拦下来）。
        if (!roleSetChanged) {
            return Decision.UNCHANGED;
        }
        return Decision.NEEDS_ASSIGN_PERMISSION;
    }
}
