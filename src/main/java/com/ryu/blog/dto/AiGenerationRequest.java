package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Schema(description = "生成模式：free=自由创作, template=模板辅助, refine=内容优化", 
            allowableValues = {"free", "template", "refine"}, 
            example = "template")
    private String mode;

    // ========== 自由模式 & 内容优化共用字段 ==========
    @Schema(description = "【自由模式】完整提示词/要求；【内容优化】优化指令", 
            example = "写一篇关于Spring AI的深度教程 / 扩展这段内容，增加更多技术细节")
    private String prompt;

    // ========== 模板模式字段 ==========
    @Schema(description = "【模板模式】模板ID", example = "1")
    private Long templateId;

    @Schema(description = "【模板模式】动态字段值（根据模板定义的字段填充）", 
            example = "{\"topic\": \"Spring AI入门\", \"style\": \"教程\", \"audience\": \"初学者\"}")
    private Map<String, String> templateFields;

    // ========== 内容优化字段 ==========
    @Schema(description = "【内容优化】选中的内容", example = "Spring AI是一个强大的框架。")
    private String content;

    // ========== 通用参数（可选，用于覆盖模板默认值或补充信息） ==========
    @Schema(description = "语言（可选）", example = "zh", allowableValues = {"zh", "en"})
    private String language;

    @Schema(description = "语气（可选）", example = "professional", 
            allowableValues = {"formal", "casual", "professional", "friendly"})
    private String tone;

    @Min(value = 100, message = "长度至少100字")
    @Max(value = 10000, message = "长度最多10000字")
    @Schema(description = "期望长度（字数，可选）", example = "2000")
    private Integer length;

    @Schema(description = "风格（可选）", example = "tutorial", 
            allowableValues = {"tutorial", "review", "news", "opinion"})
    private String style;

    // ========== 高级选项 ==========
    @Schema(description = "指定提供商（可选）", example = "openai", 
            allowableValues = {"openai", "azure", "anthropic", "qwen", "gemini"})
    private String providerName;

    @Schema(description = "指定模型（可选）", example = "gpt-4")
    private String modelName;

    @Schema(description = "是否流式生成", example = "false", defaultValue = "false")
    private Boolean stream;

    @Schema(description = "用户ID（系统自动填充）")
    private Long userId;
}
