package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 权限状态更新 DTO
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Schema(description = "权限状态更新DTO")
public class PermissionsStatusDTO {
    
    /**
     * 权限ID列表
     */
    @Schema(description = "权限ID列表")
    @NotEmpty(message = "权限ID列表不能为空")
    private List<Long> ids;

    /**
     * 状态：0-禁用，1-启用
     */
    @Schema(description = "状态：0-禁用，1-启用")
    @NotNull(message = "状态不能为空")
    private Boolean isActive;
} 