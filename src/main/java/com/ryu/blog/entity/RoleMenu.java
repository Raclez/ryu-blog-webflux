package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 角色-菜单关联实体
 *
 * <p>对应 {@code t_sys_role_menus} 表 —— 这张表此前在 schema 里定义着，
 * 却<b>没有任何实体、仓储或代码引用</b>（纯孤儿表）。
 * 现在菜单可见性模型改成 {@code role → menu} 直连，它才真正开始工作。
 *
 * <p>与 {@code t_menu_permissions} 的分工：
 * <ul>
 *   <li>{@code t_sys_role_menus}：<b>能不能看到</b>某个菜单；</li>
 *   <li>{@code t_role_permissions}：<b>能不能做</b>某个操作。</li>
 * </ul>
 *
 * @author ryu
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_sys_role_menus")
@EqualsAndHashCode(callSuper = true)
public class RoleMenu extends BaseEntity {

    /**
     * 角色ID
     */
    @Column("role_id")
    private Long roleId;

    /**
     * 菜单ID
     */
    @Column("menu_id")
    private Long menuId;
}
