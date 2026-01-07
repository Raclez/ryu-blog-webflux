package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiModelInfo;
import com.ryu.blog.dto.AiProviderConfigValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Anthropic (Claude) 提供商实现（使用 ChatClient API）
 * 
 * <p>使用 Anthropic Claude 模型提供 AI 内容生成能力。
 * 支持 Claude 3 Opus、Claude 3 Sonnet、Claude 3 Haiku 等模型。
 * 
 * <p>配置要求：
 * - apiKey: Anthropic API 密钥
 * - modelName: 模型名称 (如: claude-3-opus-20240229, claude-3-sonnet-20240229)
 * - temperature: 温度参数 (0.0-1.0，默认 0.7)
 * - maxTokens: 最大令牌数 (默认 2000)
 * 
 * <p>Claude 模型特点：
 * - Claude 3 Opus: 最强大的模型，适合复杂任务
 * - Claude 3 Sonnet: 平衡性能和成本
 * - Claude 3 Haiku: 最快速和经济的模型
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class AnthropicProvider extends AbstractChatClientProvider {

    private static final String PROVIDER_NAME = "anthropic";
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;

    /**
     * 构造函数
     * 
     * @param apiKey Anthropic API 密钥
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大令牌数
     */
    public AnthropicProvider(
            String apiKey,
            String modelName,
            Double temperature,
            Integer maxTokens) {
        super(createChatClient(apiKey, modelName, temperature, maxTokens));
        
        this.modelName = modelName != null ? modelName : AnthropicApi.ChatModel.CLAUDE_3_SONNET.getValue();
        this.temperature = temperature != null ? temperature : 0.7;
        this.maxTokens = maxTokens != null ? maxTokens : 2000;

        log.info("Anthropic Provider 初始化成功（使用ChatClient）: model={}, temperature={}, maxTokens={}",
                this.modelName, this.temperature, this.maxTokens);
    }

    /**
     * 创建 ChatClient 实例
     */
    private static ChatClient createChatClient(
            String apiKey,
            String modelName,
            Double temperature,
            Integer maxTokens) {
        
        var anthropicApi = new AnthropicApi(apiKey);

        var options = AnthropicChatOptions.builder()
                .model(modelName != null ? modelName : AnthropicApi.ChatModel.CLAUDE_3_SONNET.getValue())
                .temperature(temperature != null ? temperature : 0.7)
                .maxTokens(maxTokens != null ? maxTokens : 2000)
                .build();

        var chatModel = new AnthropicChatModel(anthropicApi, options);
        return ChatClient.builder(chatModel).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected org.springframework.ai.chat.prompt.ChatOptions buildChatOptions(AiGenerationRequest request) {
        return AnthropicChatOptions.builder()
                .model(request.getModelName() != null ? request.getModelName() : modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
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
                        .modelId(AnthropicApi.ChatModel.CLAUDE_3_OPUS.getValue())
                        .modelName("Claude 3 Opus")
                        .providerName(PROVIDER_NAME)
                        .description("最强大的 Claude 模型，适合复杂任务")
                        .maxTokens(4096)
                        .available(true)
                        .build(),
                AiModelInfo.builder()
                        .modelId(AnthropicApi.ChatModel.CLAUDE_3_SONNET.getValue())
                        .modelName("Claude 3 Sonnet")
                        .providerName(PROVIDER_NAME)
                        .description("平衡性能和成本的 Claude 模型")
                        .maxTokens(4096)
                        .available(true)
                        .build(),
                AiModelInfo.builder()
                        .modelId(AnthropicApi.ChatModel.CLAUDE_3_HAIKU.getValue())
                        .modelName("Claude 3 Haiku")
                        .providerName(PROVIDER_NAME)
                        .description("最快速和经济的 Claude 模型")
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
                log.error("Anthropic配置验证失败", e);
                return false;
            }
        })
        .timeout(Duration.ofSeconds(10))
        .onErrorReturn(false);
    }
}
