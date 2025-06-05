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

} 