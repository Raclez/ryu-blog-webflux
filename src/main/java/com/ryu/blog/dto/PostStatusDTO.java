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
    @Min(value = 0, message = "状态值只能为0、1或2")
    @Max(value = 2, message = "状态值只能为0、1或2")
    @Schema(description = "文章状态：0-草稿，1-已发布，2-回收站", example = "1")
    private Integer status;
} 