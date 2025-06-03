package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
* 定义系统中权限和菜单关联
*
* @author ryu 475118582@qq.com
* @since 1.0.0 2024-08-27
*/
@Data
@Schema(description = "定义系统中权限和菜单关联")
public class PermissionMenusVO implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long permissionId;

	private Long menuId;


}