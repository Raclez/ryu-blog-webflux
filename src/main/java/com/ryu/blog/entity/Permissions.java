package com.ryu.blog.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

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
@EqualsAndHashCode(callSuper = true, of = {"id"})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Permissions extends BaseEntity {

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
     * 是否激活 (1: 激活, 0: 禁用)
     */
    @Schema(description = "是否激活 (1: 激活, 0: 禁用)")
    private Integer isActive;

    /**
     * 权限描述
     */
    @Schema(description = "权限描述")
    private String description;
} 