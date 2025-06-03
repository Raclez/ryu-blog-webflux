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
 * 用户角色关联实体类
 * 用于实现用户与角色的多对多关联
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_user_roles")
public class UserRole {
    
    /**
     * 主键ID
     */
    @Id
    private Long id;
    
    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;
    
    /**
     * 角色ID
     */
    @Column("role_id")
    private Long roleId;
    
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