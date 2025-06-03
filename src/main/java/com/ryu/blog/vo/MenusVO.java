package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
* 定义系统中菜单信息
*
* @author ryu 475118582@qq.com
* @since 1.0.0 2024-08-27
*/
@Data
@Schema(description = "定义系统中菜单信息")
public class MenusVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "菜单项唯一标识")
	private Long id;

	@Schema(description = "菜单名称")
	private String name;

	@Schema(description = "菜单链接")
	private String url;

	@Schema(description = "父菜单ID，用于层级结构")
	private Long parentId;

	@Schema(description = "菜单图标")
	private String icon;

	@Schema(description = "菜单项的排序号")
	private Integer sort;

	@Schema(description = "关联的权限ID，用户具有该权限时可见此菜单")
	private Long permissionId;

	@Schema(description = "菜单分类ID，用于组织菜单项")
	private Integer menuType;

	@Schema(description = "是否启用")
	private Integer isActive;


}