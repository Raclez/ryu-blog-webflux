package com.ryu.blog.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分类统计视图对象
 * 用于展示分类的统计信息，如包含的文章数量等
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "分类统计视图对象")
public class CategoryStatsVO {

    @Schema(description = "分类ID")
    @JsonSerialize(using = ToStringSerializer.class) // 确保JSON序列化时转换为字符串
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "分类描述")
    private String description;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "文章数量")
    private Integer postCount;
} 