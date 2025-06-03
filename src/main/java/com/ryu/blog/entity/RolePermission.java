package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 角色权限关联实体类
 * 用于实现角色与权限的多对多关联
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_role_permissions")
public class RolePermission {
    
    /**
     * 主键ID
     */
    @Id
    private Long id;
    
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
    
    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;
} 