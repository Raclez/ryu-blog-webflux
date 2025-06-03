package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色状态更新 DTO
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Schema(description = "角色状态更新DTO")
public class RoleStatusDTO {
    
    /**
     * 角色ID
     */
    @Schema(description = "角色ID")
    private Long roleId;
    
    /**
     * 状态：0-禁用，1-启用
     */
    @Schema(description = "状态：0-禁用，1-启用")
    private Integer isActive;
} 