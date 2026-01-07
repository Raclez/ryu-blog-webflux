package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI生成请求DTO
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI生成请求")
public class AiGenerationRequest {

    @NotBlank(message = "主题不能为空")
    @Schema(description = "生成主题", required = true, example = "如何使用Spring AI构建智能应用")
    private String topic;

    @Schema(description = "现有内容（用于优化场景）", example = "这是一篇关于Spring AI的文章...")
    private String content;

    @Schema(description = "语言", example = "zh", defaultValue = "zh")
    private String language;

    @Schema(description = "语气", example = "professional", allowableValues = {"formal", "casual", "professional", "friendly"})
    private String tone;

    @Min(value = 100, message = "长度至少100字")
    @Max(value = 10000, message = "长度最多10000字")
    @Schema(description = "期望长度（字数）", example = "1000")
    private Integer length;

    @Schema(description = "风格", example = "tutorial", allowableValues = {"tutorial", "review", "news", "opinion"})
    private String style;

    @Schema(description = "指定提供商", example = "openai", allowableValues = {"openai", "azure", "anthropic", "qwen", "gemini"})
    private String providerName;

    @Schema(description = "指定模型", example = "gpt-4")
    private String modelName;

    @Schema(description = "模板ID（使用模板生成时）", example = "1")
    private Long templateId;

    @Schema(description = "模板变量（使用模板生成时）", example = "{\"topic\": \"Spring AI\", \"level\": \"intermediate\"}")
    private Map<String, String> variables;

    @Schema(description = "是否流式生成", example = "false", defaultValue = "false")
    private Boolean stream;

//    @Schema(description = "用户ID（系统自动填充）", hidden = true)
    @Schema(description = "用户ID（系统自动填充）")
    private Long userId;

    @Schema(description = "优化操作类型", example = "expand", allowableValues = {"expand", "summarize", "rewrite", "translate"})
    private String refinementOperation;
}
