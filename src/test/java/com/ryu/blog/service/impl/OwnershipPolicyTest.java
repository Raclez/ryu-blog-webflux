package com.ryu.blog.service.impl;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.exception.PermissionDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 归属判定规则的单元测试
 *
 * <p>这条规则是安全边界：整改前 {@code PUT /users/edit/{id}}、
 * {@code GET /permissions/user/{userId}} 等端点没有任何归属校验，
 * 任意登录用户都能读写他人数据。
 *
 * <p>规则本身是纯函数，所以不 mock 任何东西 —— 依赖越少，测试越不容易假失败。
 */
class OwnershipPolicyTest {

    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_ID = 200L;

    /** 普通用户：无「管理他人」能力 */
    private static final OperatorContext NORMAL = new OperatorContext(OWNER_ID, false);
    /** 管理员：持有 system:user:manage */
    private static final OperatorContext ADMIN = new OperatorContext(OTHER_ID, true);

    @Test
    @DisplayName("本人操作自己的数据 → 允许")
    void ownerCanActOnOwnResource() {
        assertTrue(OwnershipPolicy.canActOn(NORMAL, OWNER_ID));
        assertTrue(OwnershipPolicy.isSelf(NORMAL, OWNER_ID));
    }

    @Test
    @DisplayName("普通用户操作他人数据 → 拒绝")
    void normalUserCannotActOnOthersResource() {
        assertFalse(OwnershipPolicy.canActOn(NORMAL, OTHER_ID));
        assertFalse(OwnershipPolicy.isSelf(NORMAL, OTHER_ID));
    }

    @Test
    @DisplayName("管理员操作他人数据 → 允许")
    void adminCanActOnOthersResource() {
        assertTrue(OwnershipPolicy.canActOn(ADMIN, OWNER_ID));
        assertFalse(OwnershipPolicy.isSelf(ADMIN, OWNER_ID));
    }

    @Test
    @DisplayName("资源没有归属概念（ownerId 为 null）→ 允许")
    void resourceWithoutOwnerIsAllowed() {
        assertTrue(OwnershipPolicy.canActOn(NORMAL, null));
        assertTrue(OwnershipPolicy.canActOn(ADMIN, null));
    }

    @Test
    @DisplayName("没有操作者上下文 → 一律拒绝")
    void nullContextIsRejected() {
        assertFalse(OwnershipPolicy.canActOn(null, OWNER_ID));
        assertFalse(OwnershipPolicy.canActOn(null, null));
        assertFalse(OwnershipPolicy.isSelf(null, OWNER_ID));
    }

    @Test
    @DisplayName("未登录的匿名上下文 → 拒绝操作他人")
    void anonymousCannotActOnOthersResource() {
        OperatorContext anonymous = OperatorContext.ANONYMOUS;

        assertFalse(OwnershipPolicy.canActOn(anonymous, OWNER_ID));
        assertFalse(OwnershipPolicy.isSelf(anonymous, OWNER_ID));
        assertFalse(anonymous.isAuthenticated());
    }

    @Test
    @DisplayName("requireCanActOn：越权时抛 403")
    void requireCanActOnThrowsWhenDenied() {
        assertThrows(PermissionDeniedException.class,
                () -> OwnershipPolicy.requireCanActOn(NORMAL, OTHER_ID));

        // 本人与管理员都不应抛
        OwnershipPolicy.requireCanActOn(NORMAL, OWNER_ID);
        OwnershipPolicy.requireCanActOn(ADMIN, OWNER_ID);
    }

    @Test
    @DisplayName("requireIsSelf：管理员也不能操作别人的自助数据")
    void requireIsSelfRejectsEvenAdmin() {
        assertThrows(PermissionDeniedException.class,
                () -> OwnershipPolicy.requireIsSelf(ADMIN, OWNER_ID));

        OwnershipPolicy.requireIsSelf(NORMAL, OWNER_ID);
    }
}
