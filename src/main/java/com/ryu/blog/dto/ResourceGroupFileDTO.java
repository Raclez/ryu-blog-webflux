package com.ryu.blog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 资源组文件关联数据传输对象
 *
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceGroupFileDTO {
    
    /**
     * 资源组ID
     */
    @NotNull(message = "资源组ID不能为空")
    private Long groupId;
    
    /**
     * 文件ID列表
     */
    @NotNull(message = "文件ID列表不能为空")
    private List<Long> fileIds;
} 