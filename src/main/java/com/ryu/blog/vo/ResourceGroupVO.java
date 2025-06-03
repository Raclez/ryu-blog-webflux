package com.ryu.blog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源分组视图对象
 *
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceGroupVO {
    
    /**
     * 分组ID
     */
    private Long id;
    
    /**
     * 分组名称
     */
    private String groupName;
    
    /**
     * 分组描述
     */
    private String description;
    
    /**
     * 创建者ID
     */
    private Long creatorId;
    
    /**
     * 创建者名称
     */
    private String creatorName;
    
    /**
     * 分组类型
     */
    private Integer groupType;
    
    /**
     * 父分组ID
     */
    private Long parentId;
    
    /**
     * 排序序号
     */
    private Integer sortOrder;
} 