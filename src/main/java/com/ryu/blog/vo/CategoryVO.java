package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 分类信息视图对象
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分类信息视图对象")
public class CategoryVO {

    @Schema(description = "分类的唯一标识")
    private Long id;

    @Schema(description = "分类名称，必须唯一")
    private String name;

    @Schema(description = "分类的描述信息")
    private String description;

}