package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
@Schema(description = "角色添加/修改DTO")
public class RoleDTO {
    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称", required = true)
    private String name;

    /**
     * 角色描述
     */
    @Schema(description = "角色描述")
    private String description;
    
    /**
     * 角色编码，用于权限控制
     */
    @NotBlank(message = "角色编码不能为空")
    @Schema(description = "角色编码，用于权限控制", required = true)
    private String code;
    
    /**
     * 角色排序
     */
    @Schema(description = "角色排序")
    private Integer sort;
}
