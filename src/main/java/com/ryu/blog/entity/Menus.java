package com.ryu.blog.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 菜单实体类
 * 系统菜单定义，包含菜单、目录和按钮三种类型
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-27
 */
@Data
@Table("t_menus")
@Schema(description = "菜单实体")
public class Menus {
    
    /**
     * 菜单ID
     */
    @Id
    @Schema(description = "菜单ID")
    private Long id;
    
    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称")
    private String name;
    
    /**
     * 菜单图标
     */
    @Schema(description = "菜单图标")
    private String icon;
    
    /**
     * 路由路径
     */
    @Schema(description = "路由路径")
    private String path;
    
    /**
     * 组件路径
     */
    @Schema(description = "组件路径")
    private String component;
    
    /**
     * 菜单类型 (0: 目录, 1: 菜单, 2: 按钮)
     */
    @Schema(description = "菜单类型 (0: 目录, 1: 菜单, 2: 按钮)")
    private Integer type;
    
    /**
     * 父菜单ID
     */
    @Schema(description = "父菜单ID")
    private Long parentId;
    
    /**
     * 排序
     */
    @Schema(description = "排序")
    private Integer sort;
    
    /**
     * 是否隐藏 (0: 显示, 1: 隐藏)
     */
    @Schema(description = "是否隐藏 (0: 显示, 1: 隐藏)")
    private Integer hidden;
    
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
     * 重定向地址
     */
    @Schema(description = "重定向地址")
    private String redirect;
    
    /**
     * 是否为外链 (0: 否, 1: 是)
     */
    @Schema(description = "是否为外链 (0: 否, 1: 是)")
    private Integer isLink;
} 