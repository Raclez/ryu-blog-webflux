package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 角色权限关联实体类
 * 用于实现角色与权限的多对多关联
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_role_permissions")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class RolePermission extends BaseEntity {
    
    /**
     * 角色ID
     */
    @Column("role_id")
    private Long roleId;
    
    /**
     * 权限ID
     */
    @Column("permission_id")
    private Long permissionId;
} 