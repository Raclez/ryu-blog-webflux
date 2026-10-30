package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.AiModelInfo;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

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
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class QwenProvider extends AbstractOpenAiCompatibleProvider {

    private static final String PROVIDER_NAME = "qwen";
    private static final String DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MODEL = "qwen-max";
    private static final Double DEFAULT_TEMPERATURE = 0.7;
    private static final Integer DEFAULT_MAX_TOKENS = 2000;

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
        super(buildChatClient(
                        apiEndpoint != null ? apiEndpoint : DEFAULT_ENDPOINT,
                        apiKey,
                        modelName != null ? modelName : DEFAULT_MODEL,
                        temperature != null ? temperature : DEFAULT_TEMPERATURE,
                        maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS),
                modelName != null ? modelName : DEFAULT_MODEL,
                temperature != null ? temperature : DEFAULT_TEMPERATURE,
                maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS);

        log.info("Qwen Provider 初始化成功（使用ChatClient）: model={}, temperature={}, maxTokens={}",
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
}
