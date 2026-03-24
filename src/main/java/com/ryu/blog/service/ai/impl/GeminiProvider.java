package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiModelInfo;
import com.ryu.blog.dto.AiProviderConfigValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Google Gemini 提供商实现（使用 ChatClient API）
 * 
 * <p>使用 Google Gemini 大模型提供 AI 内容生成能力。
 * 通过 OpenAI 兼容的 API 接口访问 Gemini。
 * 
 * <p>配置要求：
 * - apiKey: Google AI API 密钥
 * - apiEndpoint: API 端点（默认：https://generativelanguage.googleapis.com/v1beta/openai/）
 * - modelName: 模型名称（gemini-pro, gemini-pro-vision）
 * - temperature: 温度参数（0.0-2.0，默认 0.7）
 * - maxTokens: 最大令牌数（默认 2000）
 * 
 * <p>支持的模型：
 * - gemini-pro: Gemini Pro 文本模型
 * - gemini-pro-vision: Gemini Pro 视觉模型（支持图像）
 * 
 * <p>注意：Gemini 的 API 可能需要特殊的认证方式，
 * 如果标准 OpenAI 兼容方式不工作，可能需要使用 Google 官方 SDK。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class GeminiProvider extends AbstractChatClientProvider {

    private static final String PROVIDER_NAME = "gemini";
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;

    /**
     * 构造函数
     * 
     * @param apiKey Google AI API 密钥
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
        super(createChatClient(apiKey, apiEndpoint, modelName, temperature, maxTokens));
        
        this.modelName = modelName != null ? modelName : "gemini-pro";
        this.temperature = temperature != null ? temperature : 0.7;
        this.maxTokens = maxTokens != null ? maxTokens : 2000;

        log.info("Gemini Provider 初始化成功（使用ChatClient）: model={}, temperature={}, maxTokens={}",
                this.modelName, this.temperature, this.maxTokens);
    }

    /**
     * 创建 ChatClient 实例
     */
    private static ChatClient createChatClient(
            String apiKey,
            String apiEndpoint,
            String modelName,
            Double temperature,
            Integer maxTokens) {
        
        String endpoint = apiEndpoint != null ? apiEndpoint : "https://generativelanguage.googleapis.com/v1beta/openai/";
        var openAiApi = new OpenAiApi(endpoint, apiKey);

        var options = OpenAiChatOptions.builder()
                .model(modelName != null ? modelName : "gemini-pro")
                .temperature(temperature != null ? temperature : 0.7)
                .maxTokens(maxTokens != null ? maxTokens : 2000)
                .build();

        var chatModel = new OpenAiChatModel(openAiApi, options);
        return ChatClient.builder(chatModel).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected org.springframework.ai.chat.prompt.ChatOptions buildChatOptions(AiGenerationRequest request) {
        return OpenAiChatOptions.builder()
                .model(request.getModelName() != null ? request.getModelName() : modelName)
                .temperature(temperature != null ? temperature : 0.7)
                .maxTokens(maxTokens != null ? maxTokens : 2000)
                .build();
    }

    @Override
    protected String getEffectiveModel(AiGenerationRequest request) {
        return request.getModelName() != null ? request.getModelName() : modelName;
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

    @Override
    public Mono<Boolean> validateConfig(AiProviderConfigValue config) {
        return Mono.fromCallable(() -> {
            try {
                String response = chatClient.prompt()
                        .user("Hello")
                        .call()
                        .content();
                return response != null && !response.isEmpty();
            } catch (Exception e) {
                log.error("Gemini配置验证失败", e);
                return false;
            }
        })
        .timeout(Duration.ofSeconds(10))
        .onErrorReturn(false);
    }
}
