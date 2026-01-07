package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI生成结果DTO
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI生成结果")
public class AiGenerationResult {

    @Schema(description = "生成的标题", example = "Spring AI：构建智能应用的最佳实践")
    private String title;

    @Schema(description = "生成的内容（Markdown格式）", example = "# Spring AI简介\n\nSpring AI是...")
    private String content;

    @Schema(description = "生成的摘要", example = "本文介绍了如何使用Spring AI构建智能应用...")
    private String summary;

    @Schema(description = "建议的标签", example = "[\"Spring AI\", \"人工智能\", \"Java\"]")
    private List<String> tags;

    @Schema(description = "建议的分类", example = "[\"技术\", \"教程\"]")
    private List<String> categories;

    @Schema(description = "图片关键词", example = "Spring AI, 人工智能, 技术架构")
    private String imageKeywords;

    @Schema(description = "使用的令牌数", example = "1500")
    private Integer tokenCount;

    @Schema(description = "估算成本（美元）", example = "0.045")
    private Double estimatedCost;

    @Schema(description = "使用的提供商", example = "openai")
    private String providerName;

    @Schema(description = "使用的模型", example = "gpt-4")
    private String modelName;

    @Schema(description = "生成耗时（毫秒）", example = "3500")
    private Long generationTime;

    @Schema(description = "历史记录ID", example = "123")
    private Long historyId;
}
