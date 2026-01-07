package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.*;
import com.ryu.blog.service.ai.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI提供商实现（使用 ChatClient API）
 * 
 * <p>使用Spring AI 1.0+ 的 ChatClient 统一接口实现AI内容生成功能。
 * 支持GPT-3.5、GPT-4等模型。
 * 
 * <p>注意：此类不是Spring Bean，而是由AiProviderFactory根据配置动态创建实例。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class OpenAiProvider extends AbstractChatClientProvider {

    private static final String PROVIDER_NAME = "openai";
    private final AiProviderConfigValue config;

    /**
     * 构造函数 - 根据配置创建OpenAI提供商实例
     * 
     * @param config 提供商配置
     */
    public OpenAiProvider(AiProviderConfigValue config) {
        super(createChatClient(config));
        this.config = config;
        log.info("OpenAI提供商实例创建成功（使用ChatClient），模型：{}", config.getModelName());
    }

    /**
     * 创建 ChatClient 实例
     */
    private static ChatClient createChatClient(AiProviderConfigValue config) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("OpenAI API密钥不能为空");
        }
        
        OpenAiApi openAiApi = new OpenAiApi(apiKey);
        
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelName() != null ? config.getModelName() : "gpt-4")
                .temperature(config.getTemperature() != null ? config.getTemperature() : 0.7)
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : 2000)
                .topP(config.getTopP() != null ? config.getTopP() : 1.0)
                .build();
        
        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
        return ChatClient.builder(chatModel).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected org.springframework.ai.chat.prompt.ChatOptions buildChatOptions(AiGenerationRequest request) {
        return OpenAiChatOptions.builder()
                .model(request.getModelName() != null ? request.getModelName() : config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();
    }

    @Override
    protected String getEffectiveModel(AiGenerationRequest request) {
        return request.getModelName() != null ? request.getModelName() : config.getModelName();
    }

    @Override
    public Mono<List<AiModelInfo>> getSupportedModels() {
        List<AiModelInfo> models = new ArrayList<>();
        
        // GPT-4 Turbo (最新)
        models.add(AiModelInfo.builder()
                .modelId("gpt-4-turbo-preview")
                .modelName("GPT-4 Turbo")
                .providerName(PROVIDER_NAME)
                .description("最新的GPT-4 Turbo模型，性能更强，成本更低，支持128K上下文")
                .maxTokens(128000)
                .inputPricePerK(0.01)
                .outputPricePerK(0.03)
                .supportsStreaming(true)
                .supportsFunctionCalling(true)
                .supportsVision(false)
                .available(true)
                .remark("推荐用于复杂任务")
                .build());
        
        // GPT-4
        models.add(AiModelInfo.builder()
                .modelId("gpt-4")
                .modelName("GPT-4")
                .providerName(PROVIDER_NAME)
                .description("OpenAI最先进的语言模型，理解能力强")
                .maxTokens(8192)
                .inputPricePerK(0.03)
                .outputPricePerK(0.06)
                .supportsStreaming(true)
                .supportsFunctionCalling(true)
                .supportsVision(false)
                .available(true)
                .remark("适合高质量内容生成")
                .build());
        
        // GPT-3.5 Turbo
        models.add(AiModelInfo.builder()
                .modelId("gpt-3.5-turbo")
                .modelName("GPT-3.5 Turbo")
                .providerName(PROVIDER_NAME)
                .description("快速且经济的模型，适合日常任务")
                .maxTokens(16385)
                .inputPricePerK(0.0005)
                .outputPricePerK(0.0015)
                .supportsStreaming(true)
                .supportsFunctionCalling(true)
                .supportsVision(false)
                .available(true)
                .remark("性价比最高")
                .build());
        
        return Mono.just(models);
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
                log.error("OpenAI配置验证失败", e);
                return false;
            }
        })
        .timeout(Duration.ofSeconds(10))
        .onErrorReturn(false);
    }
}
