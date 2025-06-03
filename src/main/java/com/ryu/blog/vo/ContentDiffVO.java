package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 内容差异视图对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "内容差异视图对象")
public class ContentDiffVO {
    @Schema(description = "变更字段")
    private String fieldName;

    @Schema(description = "旧值")
    private String oldValue;

    @Schema(description = "新值")
    private String newValue;
} 