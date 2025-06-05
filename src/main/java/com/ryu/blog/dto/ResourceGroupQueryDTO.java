package com.ryu.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 资源组查询数据传输对象
 *
 * @author ryu
 */
@Data
public class ResourceGroupQueryDTO {
    
    /**
     * 资源组ID（可选）
     * 当不提供时，查询所有文件；提供时，按指定资源组查询
     */
    private Long groupId;
    
    /**
     * 当前页码
     */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为1")
    private Long currentPage;
    
    /**
     * 每页数量
     */
    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    private Long pageSize;

} 