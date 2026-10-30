package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 辅助编辑请求
 *
 * <p>面向「编辑器里的一个动作」，而不是「生成一整篇文章」。
 * 动作粒度小 → token 可控 → 真实使用率高。
 *
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI辅助编辑请求")
public class AiAssistRequest {

    @NotBlank(message = "操作类型不能为空")
    @Schema(description = "操作类型", example = "polish",
            allowableValues = {"polish", "continue", "titles", "summary", "seo", "translate", "explain"})
    private String action;

    @NotBlank(message = "待处理内容不能为空")
    @Schema(description = "待处理内容（选中的正文或整篇文章）", example = "Spring AI 提供了统一的 ChatClient 抽象...")
    private String content;

    @Schema(description = "附加指令（可选）", example = "保持口语化风格，不要用书面语")
    private String instruction;

    @Min(value = 1, message = "候选数量至少1个")
    @Max(value = 10, message = "候选数量最多10个")
    @Schema(description = "候选数量（仅 titles / seo 动作使用）", example = "5", defaultValue = "5")
    private Integer count;

    @Min(value = 50, message = "续写字数至少50字")
    @Max(value = 5000, message = "续写字数最多5000字")
    @Schema(description = "期望字数（仅 continue 动作使用）", example = "500", defaultValue = "500")
    private Integer length;

    @Schema(description = "语言", example = "zh", allowableValues = {"zh", "en"})
    private String language;

    @Schema(description = "语气", example = "professional")
    private String tone;

    @Schema(description = "风格", example = "tutorial")
    private String style;

    @Schema(description = "指定提供商（可选）", example = "openai")
    private String providerName;

    @Schema(description = "指定模型（可选）", example = "gpt-4o")
    private String modelName;

    @Schema(description = "用户ID（服务端填充，忽略请求中的值）")
    private Long userId;
}
