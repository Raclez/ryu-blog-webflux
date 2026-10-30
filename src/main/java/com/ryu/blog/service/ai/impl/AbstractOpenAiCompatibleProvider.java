package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.AiGenerationRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * OpenAI 兼容协议的 Provider 基类
 *
 * <p>OpenAI / 通义千问 / DeepSeek / Gemini / Grok 这 5 家用的都是 OpenAI 兼容协议：
 * 请求格式、响应格式都一样，差别只有 baseUrl、模型名、默认参数。
 * 原先每个 Provider 都抄了一份「建 ChatClient + 建 Options + 校验配置」的样板代码，
 * 现在收敛到这里，子类只需要回答：provider 叫什么、默认端点/模型是什么、有哪些模型可选。
 *
 * <p>注意 Anthropic 与 Azure 的协议不同，不走这个基类
 * （它们各自继承 {@link AbstractChatClientProvider}）。
 *
 * @author ryu
 * @since 1.0.0
 */
public abstract class AbstractOpenAiCompatibleProvider extends AbstractChatClientProvider {

    /** 默认模型名 */
    protected final String modelName;
    /** 默认温度 */
    protected final Double temperature;
    /** 默认最大令牌数 */
    protected final Integer maxTokens;

    protected AbstractOpenAiCompatibleProvider(ChatClient chatClient,
                                              String modelName,
                                              Double temperature,
                                              Integer maxTokens) {
        super(chatClient);
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /**
     * 构建 OpenAI 兼容协议的 ChatClient
     *
     * @param baseUrl     API 根地址；为空则用官方地址
     * @param apiKey      API 密钥
     * @param modelName   模型名
     * @param temperature 温度
     * @param maxTokens  最大令牌数
     * @return ChatClient
     */
    protected static ChatClient buildChatClient(String baseUrl,
                                                 String apiKey,
                                                 String modelName,
                                                 Double temperature,
                                                 Integer maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API 密钥不能为空");
        }
        OpenAiApi api = (baseUrl == null || baseUrl.isBlank())
                ? new OpenAiApi(apiKey)
                : new OpenAiApi(baseUrl, apiKey);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        return ChatClient.builder(new OpenAiChatModel(api, options)).build();
    }

    @Override
    protected ChatOptions buildChatOptions(AiGenerationRequest request) {
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
}
