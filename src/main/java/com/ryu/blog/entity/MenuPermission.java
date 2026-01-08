package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 菜单权限关联实体
 * 用于实现菜单与权限的多对多关联
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-27
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_menu_permissions")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class MenuPermission extends BaseEntity {
    
    /**
     * 菜单ID
     */
    private Long menuId;
    
    /**
     * 权限ID
     */
    private Long permissionId;
} 