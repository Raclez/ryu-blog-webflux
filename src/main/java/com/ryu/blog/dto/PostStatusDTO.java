package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.*;

/**
 * 文章状态更新数据传输对象
 * @author ryu
 */
@Data
@Schema(description = "文章状态更新数据传输对象")
public class PostStatusDTO {
    
    @NotNull(message = "文章ID不能为空")
    @Positive(message = "文章ID必须为正数")
    @Schema(description = "文章ID", example = "1")
    private Long id;
    
    @NotNull(message = "状态不能为空")
    // 上界是 3（已归档）而不是 2 —— Posts.Status.ARCHIVED = 3，
    // 原来的 @Max(2) 让「归档」这个状态永远设不进去。
    @Min(value = 0, message = "状态值只能为0、1、2或3")
    @Max(value = 3, message = "状态值只能为0、1、2或3")
    @Schema(description = "文章状态：0-待发布，1-已发布，2-草稿，3-已归档", example = "1")
    private Integer status;
} 