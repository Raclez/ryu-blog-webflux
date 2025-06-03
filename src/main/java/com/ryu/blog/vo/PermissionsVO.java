package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
* 定义系统中各个操作的权限
*
* @author ryu 475118582@qq.com
* @since 1.0.0 2024-08-10
*/
@Data
@Schema(description = "定义系统中各个操作的权限")
public class PermissionsVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "权限的唯一标识")
	private Long id;

	@Schema(description = "权限名称，必须唯一")
	private String name;

	@Schema(description = "权限的描述信息")
	private String identity;


}