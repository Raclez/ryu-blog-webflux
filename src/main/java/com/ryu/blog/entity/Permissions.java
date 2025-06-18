package com.ryu.blog.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 权限实体类
 * 系统权限定义，用于细粒度的访问控制
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Table("t_permissions")
@Schema(description = "权限实体")
public class Permissions {

    /**
     * 权限ID
     */
    @Id
    @Schema(description = "权限ID")
    private Long id;

    /**
     * 权限名称
     */
    @Schema(description = "权限名称")
    private String name;

    /**
     * 权限标识，包含模块前缀，如 system:user:create
     */
    @Schema(description = "权限标识，包含模块前缀，如 system:user:create")
    private String identity;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 是否激活 (1: 激活, 0: 禁用)
     */
    @Schema(description = "是否激活 (1: 激活, 0: 禁用)")
    private Boolean isActive;

    /**
     * 权限描述
     */
    @Schema(description = "权限描述")
    private String description;

    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Boolean isDeleted;
} 