package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.AiModelInfo;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Google Gemini 提供商实现（使用 ChatClient API）
 *
 * <p>Google 提供 OpenAI 兼容的 API 接口。
 *
 * <p>配置要求：
 * - apiKey: Gemini API 密钥
 * - apiEndpoint: API 端点（默认：https://generativelanguage.googleapis.com/v1beta/openai/）
 * - modelName: 模型名称（gemini-pro, gemini-pro-vision）
 * - temperature: 温度参数（0.0-2.0，默认 0.7）
 * - maxTokens: 最大令牌数（默认 2000）
 *
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class GeminiProvider extends AbstractOpenAiCompatibleProvider {

    private static final String PROVIDER_NAME = "gemini";
    private static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/";
    private static final String DEFAULT_MODEL = "gemini-pro";
    private static final Double DEFAULT_TEMPERATURE = 0.7;
    private static final Integer DEFAULT_MAX_TOKENS = 2000;

    /**
     * 构造函数
     *
     * @param apiKey Gemini API 密钥
     * @param apiEndpoint API 端点
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大令牌数
     */
    public GeminiProvider(
            String apiKey,
            String apiEndpoint,
            String modelName,
            Double temperature,
            Integer maxTokens) {
        super(buildChatClient(
                        apiEndpoint != null ? apiEndpoint : DEFAULT_ENDPOINT,
                        apiKey,
                        modelName != null ? modelName : DEFAULT_MODEL,
                        temperature != null ? temperature : DEFAULT_TEMPERATURE,
                        maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS),
                modelName != null ? modelName : DEFAULT_MODEL,
                temperature != null ? temperature : DEFAULT_TEMPERATURE,
                maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS);

        log.info("Gemini Provider 初始化成功（使用ChatClient）: model={}, temperature={}, maxTokens={}",
                this.modelName, this.temperature, this.maxTokens);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Mono<List<AiModelInfo>> getSupportedModels() {
        return Mono.just(List.of(
                AiModelInfo.builder()
                        .modelId("gemini-pro")
                        .modelName("Gemini Pro")
                        .providerName(PROVIDER_NAME)
                        .description("Google Gemini Pro 文本模型")
                        .maxTokens(32768)
                        .available(true)
                        .build(),
                AiModelInfo.builder()
                        .modelId("gemini-pro-vision")
                        .modelName("Gemini Pro Vision")
                        .providerName(PROVIDER_NAME)
                        .description("Google Gemini Pro 视觉模型（支持图像）")
                        .maxTokens(16384)
                        .available(true)
                        .build()
        ));
    }
}
