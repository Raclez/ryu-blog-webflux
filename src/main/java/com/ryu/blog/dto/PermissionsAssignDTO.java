package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 权限分配数据传输对象
 * 用于角色权限分配
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Schema(description = "权限分配数据传输对象")
public class PermissionsAssignDTO {
    
    @NotNull(message = "角色ID不能为空")
    @Schema(description = "角色ID", required = true)
    private Long roleId;
    
    @NotEmpty(message = "权限ID列表不能为空")
    @Schema(description = "权限ID列表", required = true)
    private List<Long> permissionIds;
}
