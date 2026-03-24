package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private List<Long> ids;
    
    /**
     * 状态：0-禁用，1-启用
     */
    @Schema(description = "状态：0-禁用，1-启用")
    private Boolean isActive;
} 