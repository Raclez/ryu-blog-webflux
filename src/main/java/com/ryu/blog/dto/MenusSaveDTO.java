package com.ryu.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "菜单保存DTO")
public class MenusSaveDTO {
    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称", required = true)
    private String name;

    /**
     * 菜单链接
     */
    @Schema(description = "菜单链接")
    private String url;

    /**
     * 父菜单ID，用于层级结构
     */
    @Schema(description = "父菜单ID，用于层级结构")
    private Long parentId;

    /**
     * 菜单图标
     */
    @Schema(description = "菜单图标")
    private String icon;

    /**
     * 菜单项的排序号
     */
    @Schema(description = "菜单项的排序号")
    private Integer sort;

    /**
     * 菜单类型：0：目录, 1：菜单, 2: 按钮
     */
    @Schema(description = "菜单类型：0：目录, 1：菜单, 2: 按钮", required = true)
    private Integer menuType;
    
    /**
     * 前端组件路径
     */
    @Schema(description = "前端组件路径")
    private String component;

    /**
     * 是否激活：0-禁用，1-启用
     */
    @Schema(description = "是否激活：0-禁用，1-启用")
    private Integer isActive;
    /**
     * 是否外链：0-否，1-是
     */
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    @Schema(description = "是否外链：0-否，1-是")
    private Boolean isExternal;

    private String permission;
}
