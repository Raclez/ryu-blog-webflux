package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 标签更新数据传输对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "标签更新数据传输对象")
public class TagUpdateDTO {
    @NotNull(message = "标签ID不能为空")
    @Schema(description = "标签的ID", required = true)
    private Long id;
    
    @Size(min = 1, max = 50, message = "标签名称长度应在1-50个字符之间")
    @Schema(description = "标签名称，必须唯一")
    private String name;
    
    @Size(max = 255, message = "标签描述长度不能超过255个字符")
    @Schema(description = "标签的描述信息")
    private String description;
    
    @Size(max = 50, message = "标签别名长度不能超过50个字符")
    @Schema(description = "标签的别名，用于URL友好展示")
    private String slug;
} 