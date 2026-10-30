package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 文章置顶状态更新DTO
 *
 * <p>置顶是一个**独立于正文编辑**的开关：列表页上要能一键切换，
 * 不该要求作者把整篇文章重新提交一遍。
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "文章置顶状态更新数据传输对象")
public class PostStickyDTO {

    @NotNull(message = "文章ID不能为空")
    @Positive(message = "文章ID必须为正数")
    @Schema(description = "文章ID", example = "1")
    private Long id;

    @NotNull(message = "置顶状态不能为空")
    @Schema(description = "是否置顶：true-置顶，false-取消置顶", example = "true")
    private Boolean isSticky;
}
