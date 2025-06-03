package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分类创建数据传输对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "分类创建数据传输对象")
public class CategoryCreateDTO {
    @Schema(description = "分类名称，必须唯一")
    private String name;

    @Schema(description = "分类的描述信息")
    private String description;

    @Schema(description = "排序值")
    private Long sort;
} 