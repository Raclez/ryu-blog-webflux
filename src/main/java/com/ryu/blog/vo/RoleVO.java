package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ryu.blog.entity.Permissions;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

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
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    /**
     * 是否默认角色
     *
     * <p>类型统一为 {@code Boolean}：原先是 {@code Integer}（0/1），而实体
     * {@code Role.isDefault} 是 {@code Boolean} —— {@code BeanUtil.copyProperties}
     * 在这两种类型之间没有可靠的转换器，值可能静默变成 null。
     */
    @Schema(description = "是否默认角色")
    private Boolean isDefault;

    /**
     * 是否激活
     *
     * <p>同上，类型统一为 {@code Boolean}。
     */
    @Schema(description = "是否激活")
    private Boolean isActive;
    
    @Schema(description = "排序字段")
    private Integer sort;
    
    @Schema(description = "角色拥有的权限列表")
    private List<Permissions> permissions;
} 