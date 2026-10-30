package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 辅助动作说明
 *
 * <p>前端编辑器工具栏直接渲染这个列表，避免把动作编码写死在前端。
 *
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI辅助动作说明")
public class AiAssistAction {

    @Schema(description = "动作编码", example = "polish")
    private String code;

    @Schema(description = "动作名称", example = "润色")
    private String label;

    @Schema(description = "动作说明", example = "优化表达与结构，保持原意")
    private String description;

    @Schema(description = "是否支持流式输出", example = "true")
    private Boolean streaming;

    @Schema(description = "是否需要正文内容", example = "true")
    private Boolean requiresContent;
}
