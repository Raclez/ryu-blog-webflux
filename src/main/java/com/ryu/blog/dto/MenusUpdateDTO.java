package com.ryu.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "菜单更新DTO")
public class MenusUpdateDTO {

    /**
     * 菜单ID
     */
    @NotNull(message = "菜单ID不能为空")
    @Schema(description = "菜单ID", required = true)
    private Long id;
    
    /**
     * 菜单名称
     */
    @NotBlank(message = "菜单名称不能为空")
    @Schema(description = "菜单名称", required = true)
    private String name;

    /**
     * 前端路由名（vue-router 的 name）
     */
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
            message = "路由名只能由字母、数字、下划线组成，且以字母开头")
    @Size(max = 64, message = "路由名长度不能超过 64")
    @Schema(description = "前端路由名（vue-router 的 name）")
    private String routeName;

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
     * 菜单类型：0：目录, 1：菜单
     *
     * <p>原「2：按钮」已随「菜单↔权限」模型下线 ——
     * 按钮级能力改由 {@code t_permissions.identity} 表达，不再占用菜单行。
     */
    @NotNull(message = "菜单类型不能为空")
    @Min(value = 0, message = "菜单类型只能是 0(目录) 或 1(菜单)")
    @Max(value = 1, message = "菜单类型只能是 0(目录) 或 1(菜单)")
    @Schema(description = "菜单类型：0：目录, 1：菜单", required = true)
    private Integer menuType;
    
    /**
     * 前端组件路径
     */
    @Schema(description = "前端组件路径")
    private String component;

    /**
     * 是否隐藏：0-显示，1-隐藏
     */
    @Schema(description = "是否隐藏：0-显示，1-隐藏")
    private Integer hidden;

    /**
     * 是否外链：0-否，1-是
     */
    @Schema(description = "是否外链")
    private Boolean isLink;
    
    /**
     * 重定向地址
     */
    @Schema(description = "重定向地址")
    private String redirect;
}
