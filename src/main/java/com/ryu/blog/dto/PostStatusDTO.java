package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 文章状态更新数据传输对象
 * @author ryu
 */
@Data
@Schema(description = "文章状态更新数据传输对象")
public class PostStatusDTO {
    
    @NotNull(message = "文章ID不能为空")
    @Schema(description = "文章ID")
    private Long id;
    
    @NotNull(message = "状态不能为空")
    @Schema(description = "文章状态：0-草稿，1-已发布，2-回收站")
    private Integer status;
} 