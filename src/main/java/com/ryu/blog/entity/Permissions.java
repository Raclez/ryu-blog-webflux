package com.ryu.blog.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

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
     * 权限标识
     */
    @Schema(description = "权限标识")
    private String identity;



    /**
     * 权限模块
     */
    @Schema(description = "权限模块")
    private String module;




    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

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

    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Integer isDeleted;

} 