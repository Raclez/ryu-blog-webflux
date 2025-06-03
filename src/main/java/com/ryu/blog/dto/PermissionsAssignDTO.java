package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
    
    @Schema(description = "角色ID")
    private Long roleId;
    
    @Schema(description = "权限ID列表")
    private List<Long> permissionIds;
}
