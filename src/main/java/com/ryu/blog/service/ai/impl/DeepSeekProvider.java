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
 * DeepSeek 提供商实现（使用 ChatClient API）
 * 
 * <p>使用 DeepSeek 大模型提供 AI 内容生成能力。
 * DeepSeek 提供 OpenAI 兼容的 API 接口。
 * 
 * <p>配置要求：
 * - apiKey: DeepSeek API 密钥
 * - apiEndpoint: API 端点（默认：https://api.deepseek.com）
 * - modelName: 模型名称（deepseek-chat, deepseek-coder）
 * - temperature: 温度参数（0.0-2.0，默认 0.7）
 * - maxTokens: 最大令牌数（默认 2000）
 * 
 * <p>支持的模型：
 * - deepseek-chat: 通用对话模型
 * - deepseek-coder: 代码生成专用模型
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class DeepSeekProvider extends AbstractChatClientProvider {

    private static final String PROVIDER_NAME = "deepseek";
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;

    /**
     * 构造函数
     * 
     * @param apiKey DeepSeek API 密钥
     * @param apiEndpoint API 端点
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大令牌数
     */
    public DeepSeekProvider(
            String apiKey,
            String apiEndpoint,
            String modelName,
            Double temperature,
            Integer maxTokens) {
        super(createChatClient(apiKey, apiEndpoint, modelName, temperature, maxTokens));
        
        this.modelName = modelName != null ? modelName : "deepseek-chat";
        this.temperature = temperature != null ? temperature : 0.7;
        this.maxTokens = maxTokens != null ? maxTokens : 2000;

        log.info("DeepSeek Provider 初始化成功（使用ChatClient）: model={}, temperature={}, maxTokens={}",
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
        
        String endpoint = apiEndpoint != null ? apiEndpoint : "https://api.deepseek.com";
        var openAiApi = new OpenAiApi(endpoint, apiKey);

        var options = OpenAiChatOptions.builder()
                .model(modelName != null ? modelName : "deepseek-chat")
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
                        .modelId("deepseek-chat")
                        .modelName("DeepSeek Chat")
                        .providerName(PROVIDER_NAME)
                        .description("DeepSeek 通用对话模型")
                        .maxTokens(4096)
                        .available(true)
                        .build(),
                AiModelInfo.builder()
                        .modelId("deepseek-coder")
                        .modelName("DeepSeek Coder")
                        .providerName(PROVIDER_NAME)
                        .description("DeepSeek 代码生成专用模型")
                        .maxTokens(4096)
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
                log.error("DeepSeek配置验证失败", e);
                return false;
            }
        })
        .timeout(Duration.ofSeconds(10))
        .onErrorReturn(false);
    }
}
