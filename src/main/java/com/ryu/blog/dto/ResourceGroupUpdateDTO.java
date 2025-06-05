package com.ryu.blog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源组更新数据传输对象
 *
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceGroupUpdateDTO {
    
    /**
     * 资源组ID
     */
    @NotNull(message = "资源组ID不能为空")
    private Long id;
    
    /**
     * 资源组名称
     */
    @Size(min = 1, max = 100, message = "资源组名称长度应在1-100之间")
    private String groupName;
    
    /**
     * 资源组描述
     */
    @Size(max = 255, message = "资源组描述长度不能超过255")
    private String description;

} 