package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 批量分配角色数据传输对象
 * 用于批量为用户分配角色的请求参数
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Schema(description = "批量分配角色数据传输对象")
public class BatchAssignRoleDTO {
    
    /**
     * 用户ID列表
     */
    @Schema(description = "用户ID列表")
    private List<Long> userIds;
    
    /**
     * 角色ID
     */
    @Schema(description = "角色ID")
    private Long roleId;
    
    /**
     * 分配人
     */
    @Schema(description = "分配人")
    private String assignBy;
} 