package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiModelInfo;
import com.ryu.blog.dto.AiProviderConfigValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Azure OpenAI 提供商实现（使用 ChatClient API）
 * 
 * <p>使用 Azure OpenAI Service 提供 AI 内容生成能力。
 * 支持 GPT-4、GPT-3.5-turbo 等模型。
 * 
 * <p>配置要求：
 * - apiKey: Azure OpenAI API 密钥
 * - endpoint: Azure OpenAI 服务端点 (如: https://your-resource.openai.azure.com)
 * - deploymentName: 部署名称 (在 Azure 门户中配置的模型部署名称)
 * - temperature: 温度参数 (0.0-2.0，默认 0.7)
 * - maxTokens: 最大令牌数 (默认 2000)
 * 
 * <p>注意：Azure OpenAI 使用部署名称而不是模型名称。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public class AzureOpenAiProvider extends AbstractChatClientProvider {

    private static final String PROVIDER_NAME = "azure";
    private final String deploymentName;
    private final Double temperature;
    private final Integer maxTokens;

    /**
     * 构造函数
     * 
     * @param apiKey Azure OpenAI API 密钥
     * @param endpoint Azure OpenAI 服务端点
     * @param deploymentName 部署名称
     * @param temperature 温度参数
     * @param maxTokens 最大令牌数
     */
    public AzureOpenAiProvider(
            String apiKey,
            String endpoint,
            String deploymentName,
            Double temperature,
            Integer maxTokens) {
        super(createChatClient(apiKey, endpoint, deploymentName, temperature, maxTokens));
        
        this.deploymentName = deploymentName;
        this.temperature = temperature != null ? temperature : 0.7;
        this.maxTokens = maxTokens != null ? maxTokens : 2000;

        log.info("Azure OpenAI Provider 初始化成功（使用ChatClient）: deployment={}, temperature={}, maxTokens={}",
                deploymentName, this.temperature, this.maxTokens);
    }

    /**
     * 创建 ChatClient 实例
     */
    private static ChatClient createChatClient(
            String apiKey,
            String endpoint,
            String deploymentName,
            Double temperature,
            Integer maxTokens) {
        
        var options = AzureOpenAiChatOptions.builder()
                .deploymentName(deploymentName)
                .temperature(temperature != null ? temperature : 0.7)
                .maxTokens(maxTokens != null ? maxTokens : 2000)
                .build();

        var clientBuilder = new com.azure.ai.openai.OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new com.azure.core.credential.AzureKeyCredential(apiKey));
        
        var chatModel = new AzureOpenAiChatModel(clientBuilder, options);

        return ChatClient.builder(chatModel).build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected org.springframework.ai.chat.prompt.ChatOptions buildChatOptions(AiGenerationRequest request) {
        return AzureOpenAiChatOptions.builder()
                .deploymentName(deploymentName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    @Override
    protected String getEffectiveModel(AiGenerationRequest request) {
        return deploymentName;
    }

    @Override
    public Mono<List<AiModelInfo>> getSupportedModels() {
        return Mono.just(List.of(
                AiModelInfo.builder()
                        .modelId(deploymentName)
                        .modelName(deploymentName)
                        .providerName(PROVIDER_NAME)
                        .description("Azure OpenAI 部署: " + deploymentName)
                        .maxTokens(maxTokens)
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
                log.error("Azure OpenAI配置验证失败", e);
                return false;
            }
        })
        .timeout(Duration.ofSeconds(10))
        .onErrorReturn(false);
    }
}
