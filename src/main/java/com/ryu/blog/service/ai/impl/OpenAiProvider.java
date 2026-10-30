package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.AiModelInfo;
import com.ryu.blog.dto.AiProviderConfigValue;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * OpenAI 提供商实现（使用 ChatClient API）
 *
 * <p>使用Spring AI 1.0+ 的 ChatClient 统一接口实现AI内容生成功能。
 * 支持GPT-3.5、GPT-4等模型。
 *
 * <p>注意：这个类不是 Spring Bean，而是由 {@code AiProviderFactory} 根据配置动态创建实例。
 *
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class OpenAiProvider extends AbstractOpenAiCompatibleProvider {

    private static final String PROVIDER_NAME = "openai";
    private static final String DEFAULT_MODEL = "gpt-4";
    private static final Double DEFAULT_TEMPERATURE = 0.7;
    private static final Integer DEFAULT_MAX_TOKENS = 2000;

    /**
     * 构造函数 - 根据配置创建OpenAI提供商实例
     *
     * @param config 配置
     */
    public OpenAiProvider(AiProviderConfigValue config) {
        super(buildChatClient(
                        // 端点为空则用官方地址；配了就是自建网关 / 中转 / 代理
                        config.getApiEndpoint(),
                        config.getApiKey(),
                        config.getModelName() != null ? config.getModelName() : DEFAULT_MODEL,
                        config.getTemperature() != null ? config.getTemperature() : DEFAULT_TEMPERATURE,
                        config.getMaxTokens() != null ? config.getMaxTokens() : DEFAULT_MAX_TOKENS),
                config.getModelName() != null ? config.getModelName() : DEFAULT_MODEL,
                config.getTemperature() != null ? config.getTemperature() : DEFAULT_TEMPERATURE,
                config.getMaxTokens() != null ? config.getMaxTokens() : DEFAULT_MAX_TOKENS);

        log.info("OpenAI提供商实例创建成功（使用ChatClient），模型：{}", this.modelName);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Mono<List<AiModelInfo>> getSupportedModels() {
        return Mono.just(List.of(
                AiModelInfo.builder()
                        .modelId("gpt-4o")
                        .modelName("GPT-4o")
                        .providerName(PROVIDER_NAME)
                        .description("多模态旗舰模型，速度与性价比俱佳")
                        .maxTokens(128000)
                        .inputPricePerK(0.0025)
                        .outputPricePerK(0.01)
                        .supportsStreaming(true)
                        .supportsFunctionCalling(true)
                        .supportsVision(true)
                        .available(true)
                        .remark("推荐默认使用")
                        .build(),
                AiModelInfo.builder()
                        .modelId("gpt-4o-mini")
                        .modelName("GPT-4o mini")
                        .providerName(PROVIDER_NAME)
                        .description("轻量版本，成本极低")
                        .maxTokens(128000)
                        .inputPricePerK(0.00015)
                        .outputPricePerK(0.0006)
                        .supportsStreaming(true)
                        .supportsFunctionCalling(true)
                        .supportsVision(true)
                        .available(true)
                        .remark("适合大批量简单任务")
                        .build(),
                AiModelInfo.builder()
                        .modelId("gpt-4-turbo")
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
                        .build(),
                AiModelInfo.builder()
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
                        .build(),
                AiModelInfo.builder()
                        .modelId("gpt-3.5-turbo")
                        .modelName("GPT-3.5 Turbo")
                        .providerName(PROVIDER_NAME)
                        .description("快速和经济的模型，适合日常任务")
                        .maxTokens(16385)
                        .inputPricePerK(0.0005)
                        .outputPricePerK(0.0015)
                        .supportsStreaming(true)
                        .supportsFunctionCalling(true)
                        .supportsVision(false)
                        .available(true)
                        .remark("性价比最高")
                        .build()
        ));
    }
}
