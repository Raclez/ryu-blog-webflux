package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 标签创建数据传输对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "标签创建数据传输对象")
public class TagCreateDTO {
    @NotBlank(message = "标签名称不能为空")
    @Size(min = 1, max = 50, message = "标签名称长度应在1-50个字符之间")
    @Schema(description = "标签名称，必须唯一", required = true)
    private String name;
    
    @Size(max = 255, message = "标签描述长度不能超过255个字符")
    @Schema(description = "标签的描述信息")
    private String description;
    
    @Size(max = 50, message = "标签别名长度不能超过50个字符")
    @Schema(description = "标签的别名，用于URL友好展示")
    private String slug;
} 