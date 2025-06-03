package com.ryu.blog.vo;

import com.ryu.blog.entity.Permissions;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图对象
 * 用于返回角色详细信息
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Data
@Schema(description = "角色视图对象")
public class RoleVO {
    
    @Schema(description = "角色ID")
    private Long id;
    
    @Schema(description = "角色名称")
    private String name;
    
    @Schema(description = "角色编码")
    private String code;
    
    @Schema(description = "角色描述")
    private String description;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    
    @Schema(description = "是否默认角色 (1: 是, 0: 否)")
    private Integer isDefault;
    
    @Schema(description = "是否激活 (1: 激活, 0: 禁用)")
    private Integer isActive;
    
    @Schema(description = "排序字段")
    private Integer sort;
    
    @Schema(description = "角色拥有的权限列表")
    private List<Permissions> permissions;
} 