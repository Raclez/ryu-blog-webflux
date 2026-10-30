package com.ryu.blog.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 实体审计字段默认值的单元测试
 *
 * <p>这条规则看着不起眼，却决定了一整类「Column 'x' cannot be null」：
 * Spring Data R2DBC 会把实体的 null 属性<b>显式写成 NULL</b>，数据库的 DEFAULT 不生效；
 * 而 {@code BaseEntity.onCreate()} 从未被框架调用过。
 *
 * <p>所以审计字段必须靠<b>字段默认值</b>兜底 —— 而且 Lombok 的
 * {@code @SuperBuilder} 会忽略字段初始值，不加 {@code @Builder.Default}
 * 时 builder 建出来的实体全是 null。
 */
class BaseEntityDefaultsTest {

    @Test
    @DisplayName("new 出来的实体：审计字段不为 null")
    void newInstanceHasAuditDefaults() {
        Role role = new Role();

        assertNotNull(role.getCreateTime(), "createTime 不能为 null");
        assertNotNull(role.getUpdateTime(), "updateTime 不能为 null");
        assertNotNull(role.getIsDeleted(), "isDeleted 不能为 null");
        assertFalse(role.getIsDeleted(), "isDeleted 默认应为 false（未删除）");
    }

    @Test
    @DisplayName("builder 建出来的实体：审计字段不为 null")
    void builderHasAuditDefaults() {
        // 这条最容易回归：Lombok 的 @SuperBuilder 会忽略字段初始值，
        // 必须靠 @Builder.Default 才能把默认值带进 builder
        RolePermission rp = RolePermission.builder()
                .roleId(1L)
                .permissionId(2L)
                .build();

        assertNotNull(rp.getCreateTime(), "builder 建出来的 createTime 不能为 null");
        assertNotNull(rp.getUpdateTime(), "builder 建出来的 updateTime 不能为 null");
        assertNotNull(rp.getIsDeleted(), "builder 建出来的 isDeleted 不能为 null");
        assertFalse(rp.getIsDeleted(), "builder 建出来的 isDeleted 默认应为 false");
    }

    @Test
    @DisplayName("显式设置的值不会被默认值覆盖")
    void explicitValuesWin() {
        RoleMenu roleMenu = RoleMenu.builder()
                .roleId(1L)
                .menuId(2L)
                .isDeleted(true)
                .build();

        assertEquals(true, roleMenu.getIsDeleted());
    }
}
