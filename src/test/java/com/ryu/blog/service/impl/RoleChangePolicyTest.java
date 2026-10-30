package com.ryu.blog.service.impl;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.service.impl.RoleChangePolicy.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 角色变更判定规则的单元测试
 *
 * <p>这条规则是安全边界：整改前 {@code PUT /users/edit/{id}} 和
 * {@code POST /users/save} 都能顺带改角色，只要持有 {@code system:user:edit}
 * 就能给任意用户换上 admin 角色 —— 等于绕过了
 * {@code PUT /users/{id}/roles}（那个端点有 {@code system:user:assign} 闸门）。
 *
 * <p>规则本身是纯函数，所以不 mock 任何东西 —— 依赖越少，测试越不容易假失败。
 */
class RoleChangePolicyTest {

    private static final Long SELF_ID = 100L;
    private static final Long OTHER_ID = 200L;
    private static final Long OPERATOR_ID = 300L;

    /** 普通用户：无「管理他人」能力 */
    private static final OperatorContext NORMAL = new OperatorContext(SELF_ID, false);
    /** 管理员：持有 system:user:manage */
    private static final OperatorContext ADMIN = new OperatorContext(OPERATOR_ID, true);

    @Test
    @DisplayName("改自己的角色 → 忽略（不能自己给自己升权）")
    void selfRoleChangeIsIgnored() {
        assertEquals(Decision.IGNORED, RoleChangePolicy.decide(NORMAL, SELF_ID, true));
        assertEquals(Decision.IGNORED, RoleChangePolicy.decide(ADMIN, OPERATOR_ID, true));
    }

    @Test
    @DisplayName("改别人的角色但角色没变 → 不算改角色，不额外要权限")
    void unchangedRoleSetDoesNotRequireAssignPermission() {
        assertEquals(Decision.UNCHANGED, RoleChangePolicy.decide(ADMIN, OTHER_ID, false));
    }

    @Test
    @DisplayName("改别人的角色 → 需要 system:user:assign")
    void changingOthersRolesRequiresAssignPermission() {
        assertEquals(Decision.NEEDS_ASSIGN_PERMISSION, RoleChangePolicy.decide(ADMIN, OTHER_ID, true));
    }

    @Test
    @DisplayName("没有操作者上下文 → 一律按「要权限」处理")
    void nullContextIsTreatedAsDenied() {
        assertEquals(Decision.NEEDS_ASSIGN_PERMISSION, RoleChangePolicy.decide(null, OTHER_ID, true));
    }
}
