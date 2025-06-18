package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色更新 DTO
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Schema(description = "角色更新DTO")
public class RoleUpdateDTO {
    
    /**
     * 角色ID
     */
    @Schema(description = "角色ID")
    private Long id;
    
    /**
     * 角色名称
     */
    @Schema(description = "角色名称")
    private String name;
    
    /**
     * 角色编码
     */
    @Schema(description = "角色编码")
    private String code;
    
    /**
     * 角色描述
     */
    @Schema(description = "角色描述")
    private String description;
    
    /**
     * 角色排序
     */
    @Schema(description = "角色排序")
    private Integer sort;
    
    /**
     * 是否激活：0-禁用，1-启用
     */
    @Schema(description = "是否激活：0-禁用，1-启用")
    private Boolean isActive;
} 