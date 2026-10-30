package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 辅助编辑结果
 *
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI辅助编辑结果")
public class AiAssistResult {

    @Schema(description = "操作类型", example = "polish")
    private String action;

    @Schema(description = "主结果文本。titles 动作为第一个候选标题，seo 动作为描述文本", example = "润色后的正文")
    private String content;

    @Schema(description = "候选结果（如多个标题、SEO标题）", example = "[\"标题一\", \"标题二\"]")
    private List<String> alternatives;

    @Schema(description = "总令牌数", example = "1500")
    private Integer tokenCount;

    @Schema(description = "输入令牌数", example = "800")
    private Integer promptTokens;

    @Schema(description = "输出令牌数", example = "700")
    private Integer completionTokens;

    @Schema(description = "估算成本（美元）", example = "0.012")
    private Double estimatedCost;

    @Schema(description = "使用的提供商", example = "openai")
    private String providerName;

    @Schema(description = "使用的模型", example = "gpt-4o")
    private String modelName;

    @Schema(description = "生成耗时（毫秒）", example = "3500")
    private Long generationTime;

    @Schema(description = "历史记录ID", example = "123")
    private Long historyId;
}
