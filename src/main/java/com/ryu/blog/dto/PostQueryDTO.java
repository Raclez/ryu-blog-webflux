package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 文章查询数据传输对象
 * @author ryu
 */
@Data
@Schema(description = "文章查询数据传输对象")
public class PostQueryDTO {
    
    @NotNull(message = "页码不能为空")
    @Schema(description = "当前页码")
    private Integer current;
    
    @NotNull(message = "每页数量不能为空")
    @Schema(description = "每页数量")
    private Integer size;
    
    @Schema(description = "文章标题")
    private String title;
    
    @Schema(description = "文章状态")
    private Integer status;
    
    @Schema(description = "分类ID")
    private Long categoryId;
    
    @Schema(description = "标签ID")
    private Long tagId;
    
    @Schema(description = "开始时间")
    private String startTime;
    
    @Schema(description = "结束时间")
    private String endTime;
} 