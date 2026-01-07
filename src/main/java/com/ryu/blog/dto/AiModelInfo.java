package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI模型信息DTO
 * 
 * <p>包含AI模型的基本信息和能力描述。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI模型信息")
public class AiModelInfo {

    @Schema(description = "模型ID", example = "gpt-4", required = true)
    private String modelId;

    @Schema(description = "模型名称", example = "GPT-4", required = true)
    private String modelName;

    @Schema(description = "提供商名称", example = "openai", required = true)
    private String providerName;

    @Schema(description = "模型描述", example = "OpenAI最先进的语言模型，具有强大的理解和生成能力")
    private String description;

    @Schema(description = "最大令牌数", example = "8192")
    private Integer maxTokens;

    @Schema(description = "输入价格（每1K令牌，美元）", example = "0.03")
    private Double inputPricePerK;

    @Schema(description = "输出价格（每1K令牌，美元）", example = "0.06")
    private Double outputPricePerK;

    @Schema(description = "是否支持流式输出", example = "true")
    private Boolean supportsStreaming;

    @Schema(description = "是否支持函数调用", example = "true")
    private Boolean supportsFunctionCalling;

    @Schema(description = "是否支持视觉输入", example = "true")
    private Boolean supportsVision;

    @Schema(description = "是否可用", example = "true")
    private Boolean available;

    @Schema(description = "备注", example = "推荐用于复杂任务")
    private String remark;
}
