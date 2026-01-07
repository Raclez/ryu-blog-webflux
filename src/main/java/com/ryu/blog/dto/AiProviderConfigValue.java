package com.ryu.blog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI提供商配置值对象
 * 用于序列化到系统配置表的config_value字段
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "AI提供商配置值对象")
public class AiProviderConfigValue {

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "API密钥（加密存储）", example = "sk-xxx")
    private String apiKey;

    @Schema(description = "API端点URL", example = "https://api.openai.com")
    private String apiEndpoint;

    @Schema(description = "API版本", example = "2024-02-01")
    private String apiVersion;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "温度参数(0.0-2.0)", example = "0.7")
    private Double temperature;

    @Schema(description = "最大令牌数", example = "2000")
    private Integer maxTokens;

    @Schema(description = "Top-P采样参数(0.0-1.0)", example = "1.0")
    private Double topP;

    @Schema(description = "部署名称（Azure专用）", example = "gpt-4-deployment")
    private String deploymentName;

    /**
     * 获取端点URL（兼容性方法）
     * 支持 endpoint 和 apiEndpoint 两种字段名
     */
    public String getEndpoint() {
        return apiEndpoint;
    }

    /**
     * 设置端点URL（兼容性方法）
     */
    public void setEndpoint(String endpoint) {
        this.apiEndpoint = endpoint;
    }
}
