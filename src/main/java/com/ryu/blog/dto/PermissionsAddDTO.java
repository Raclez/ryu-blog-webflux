package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "权限添加DTO")
public class PermissionsAddDTO {

    @Schema(description = "权限名称", required = true)
    @NotBlank(message = "权限名称不能为空")
    private String name;

    @Schema(description = "权限标识，包含模块前缀，如 system:user:create", required = true)
    @NotBlank(message = "权限标识不能为空")
    private String identity;
    
    @Schema(description = "权限描述")
    private String description;
}
