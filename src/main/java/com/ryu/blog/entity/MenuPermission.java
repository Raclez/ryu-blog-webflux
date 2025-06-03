package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 菜单权限关联实体
 * 用于实现菜单与权限的多对多关联
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_menu_permissions")
public class MenuPermission {
    
    /**
     * 主键ID
     */
    @Id
    private Long id;
    
    /**
     * 菜单ID
     */
    private Long menuId;
    
    /**
     * 权限ID
     */
    private Long permissionId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
} 