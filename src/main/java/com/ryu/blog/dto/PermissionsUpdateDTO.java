package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PermissionsUpdateDTO {

    @Schema(description = "权限名称", required = true)
    private String name;

    @Schema(description = "权限标识，用于鉴权", required = true)
    private String identity;

    @Schema(description = "权限描述")
    private String description;

    @Schema(description = "所属模块", required = true)
    private String module;

    private Long id;
    private Integer isActive;
}
