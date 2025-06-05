package com.ryu.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源组创建数据传输对象
 *
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceGroupCreateDTO {
    
    /**
     * 资源组名称
     */
    @NotBlank(message = "资源组名称不能为空")
    @Size(min = 1, max = 100, message = "资源组名称长度应在1-100之间")
    private String groupName;
    
    /**
     * 资源组描述
     */
    @Size(max = 255, message = "资源组描述长度不能超过255")
    private String description;

    private Integer staus;

} 