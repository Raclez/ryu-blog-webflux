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
 * 阿里通义千问提供商实现（使用 ChatClient API）
 * 
 * <p>使用阿里云通义千问大模型提供 AI 内容生成能力。
 * 通义千问提供 OpenAI 兼容的 API 接口。
 * 
 * <p>配置要求：
 * - apiKey: 通义千问 API 密钥（在阿里云控制台获取）
 * - apiEndpoint: API 端点（默认：https://dashscope.aliyuncs.com/compatible-mode/v1）
 * - modelName: 模型名称（qwen-max, qwen-plus, qwen-turbo）
 * - temperature: 温度参数（0.0-2.0，默认 0.7）
 * - maxTokens: 最大令牌数（默认 2000）
 * 
 * <p>支持的模型：
 * - qwen-max: 最强大的模型，适合复杂任务
 * - qwen-plus: 平衡性能和成本
 * - qwen-turbo: 最快速和经济的模型
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class QwenProvider extends AbstractChatClientProvider {

    private static final String PROVIDER_NAME = "qwen";
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;

    /**
     * 构造函数
     * 
     * @param apiKey 通义千问 API 密钥
     * @param apiEndpoint API 端点
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大令牌数
     */
    public QwenProvider(
            String apiKey,
            String apiEndpoint,
            String modelName,
            Double temperature,
            Integer maxTokens) {
        super(createChatClient(apiKey, apiEndpoint, modelName, temperature, maxTokens));
        
        this.modelName = modelName != null ? modelName : "qwen-max";
        this.temperature = temperature != null ? temperature : 0.7;
        this.maxTokens = maxTokens != null ? maxTokens : 2000;

        log.info("Qwen Provider 初始化成功（使用ChatClient）: model={}, temperature={}, maxTokens={}",
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
        
        String endpoint = apiEndpoint != null ? apiEndpoint : "https://dashscope.aliyuncs.com/compatible-mode/v1";
        var openAiApi = new OpenAiApi(endpoint, apiKey);

        var options = OpenAiChatOptions.builder()
                .model(modelName != null ? modelName : "qwen-max")
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
                        .modelId("qwen-max")
                        .modelName("Qwen Max")
                        .providerName(PROVIDER_NAME)
                        .description("最强大的通义千问模型，适合复杂任务")
                        .maxTokens(8000)
                        .available(true)
                        .build(),
                AiModelInfo.builder()
                        .modelId("qwen-plus")
                        .modelName("Qwen Plus")
                        .providerName(PROVIDER_NAME)
                        .description("平衡性能和成本的通义千问模型")
                        .maxTokens(8000)
                        .available(true)
                        .build(),
                AiModelInfo.builder()
                        .modelId("qwen-turbo")
                        .modelName("Qwen Turbo")
                        .providerName(PROVIDER_NAME)
                        .description("最快速和经济的通义千问模型")
                        .maxTokens(8000)
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
                log.error("Qwen配置验证失败", e);
                return false;
            }
        })
        .timeout(Duration.ofSeconds(10))
        .onErrorReturn(false);
    }
}
