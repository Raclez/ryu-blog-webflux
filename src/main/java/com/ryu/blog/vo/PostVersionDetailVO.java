package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文章版本详情视图对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostVersionDetailVO extends PostVersionVO {
    private static final long serialVersionUID = 5831843254176767540L;
    
    @Schema(description = "版本完整内容")
    private String content;

    @Schema(description = "差异变更项")
    private List<ContentDiffVO> diffs;
} 