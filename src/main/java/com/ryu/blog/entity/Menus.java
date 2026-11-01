package com.ryu.blog.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

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
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Menus extends BaseEntity {
    
    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称")
    private String name;

    /**
     * 前端路由名（vue-router 的 name）
     *
     * <p>菜单名是中文、可以随时改；路由名必须 ASCII 且稳定 ——
     * 前端的 keep-alive、按 name 去重、以及「后端驱动菜单」模式下
     * {@code generateRoutesByBackend} 都拿它当路由标识。
     */
    @Schema(description = "前端路由名（vue-router 的 name）")
    private String routeName;
    
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
     * 菜单类型 (0: 目录, 1: 菜单)
     *
     * <p>原「2: 按钮」已随「菜单↔权限」模型下线 —— 按钮级能力改由
     * {@code t_permissions.identity} 表达。
     */
    @Schema(description = "菜单类型 (0: 目录, 1: 菜单)")
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
     * 重定向地址
     */
    @Schema(description = "重定向地址")
    private String redirect;
    
    /**
     * 是否为外链 (0: 否, 1: 是)
     */
    @Schema(description = "是否为外链 (0: 否, 1: 是)")
    private Boolean isLink;
} 