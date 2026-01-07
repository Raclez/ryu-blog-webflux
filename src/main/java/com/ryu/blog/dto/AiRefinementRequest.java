package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI内容优化请求DTO
 * 
 * <p>用于优化现有内容的请求，支持多种优化操作。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI内容优化请求")
public class AiRefinementRequest {

    @NotBlank(message = "原始内容不能为空")
    @Schema(description = "原始内容", required = true, example = "这是一篇需要优化的文章...")
    private String content;

    @NotBlank(message = "优化操作不能为空")
    @Schema(
        description = "优化操作类型", 
        required = true,
        example = "expand",
        allowableValues = {"expand", "summarize", "rewrite", "translate", "improve_seo", "polish"}
    )
    private String operation;

    @Schema(description = "目标语言（用于翻译操作）", example = "en")
    private String targetLanguage;

    @Schema(description = "优化指令（额外的优化说明）", example = "使内容更专业，增加技术细节")
    private String instruction;

    @Schema(description = "期望长度（字数，用于扩展或摘要）", example = "2000")
    private Integer targetLength;

    @Schema(description = "保持原始结构", example = "true", defaultValue = "true")
    private Boolean preserveStructure;

    @Schema(description = "指定提供商", example = "openai")
    private String providerName;

    @Schema(description = "指定模型", example = "gpt-4")
    private String modelName;

    @Schema(description = "用户ID（系统自动填充）", hidden = true)
    private Long userId;
}
