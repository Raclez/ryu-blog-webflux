package com.ryu.blog.service.ai.impl;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.service.ai.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * 基于 ChatClient 的抽象提供商基类
 * 
 * <p>提供通用的生成逻辑，子类只需实现特定的配置和选项构建。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractChatClientProvider implements AiProvider {

    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    
    protected final ChatClient chatClient;

    protected AbstractChatClientProvider(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Mono<AiGenerationResult> generate(AiGenerationRequest request) {
        log.debug("开始生成内容: mode={}, prompt={}", request.getMode(), 
                request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())) : "null");
        
        return Mono.fromCallable(() -> {
            // 构建 prompt
            String prompt = buildPrompt(request);
            
            // 使用 ChatClient 的 fluent API
            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .options(buildChatOptions(request))
                    .call()
                    .chatResponse();
            
            // 提取生成的内容
            String content = Objects.requireNonNull(response).getResult().getOutput().getContent();
            
            // 提取使用统计
            var usage = response.getMetadata().getUsage();
            Integer promptTokens = usage != null ? Math.toIntExact(usage.getPromptTokens()) : 0;
            Integer completionTokens = usage != null ? Math.toIntExact(usage.getGenerationTokens()) : 0;
            Integer totalTokens = usage != null ? Math.toIntExact(usage.getTotalTokens()) : 0;
            
            log.debug("内容生成成功: tokens={}", totalTokens);
            
            // 构建结果
            return AiGenerationResult.builder()
                    .content(content)
                    .modelName(getEffectiveModel(request))
                    .tokenCount(totalTokens)
                    .providerName(getProviderName())
                    .build();
        })
        .timeout(DEFAULT_TIMEOUT)
        .doOnError(error -> log.error("内容生成失败", error));
    }

    @Override
    public Flux<String> generateStream(AiGenerationRequest request) {
        log.debug("开始流式生成内容: mode={}", request.getMode());
        
        return Flux.defer(() -> {
            // 构建 prompt
            String prompt = buildPrompt(request);
            
            // 使用 ChatClient 的流式 API
            return chatClient.prompt()
                    .user(prompt)
                    .options(buildChatOptions(request))
                    .stream()
                    .content();
        })
        .timeout(DEFAULT_TIMEOUT)
        .doOnComplete(() -> log.debug("流式内容生成完成"))
        .doOnError(error -> log.error("流式内容生成失败", error));
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return Mono.fromCallable(() -> {
            try {
                String response = chatClient.prompt()
                        .user("ping")
                        .call()
                        .content();
                return response != null;
            } catch (Exception e) {
                log.warn("{}服务不可用", getProviderName(), e);
                return false;
            }
        })
        .timeout(Duration.ofSeconds(5))
        .onErrorReturn(false);
    }

    /**
     * 构建聊天选项（由子类实现）
     * 返回 ChatOptions 类型而不是 Object
     */
    protected abstract org.springframework.ai.chat.prompt.ChatOptions buildChatOptions(AiGenerationRequest request);

    /**
     * 获取实际使用的模型名称（由子类实现）
     */
    protected abstract String getEffectiveModel(AiGenerationRequest request);

    /**
     * 构建 prompt（根据请求参数和模式）
     */
    protected String buildPrompt(AiGenerationRequest request) {
        StringBuilder prompt = new StringBuilder();
        
        // 根据模式构建不同的prompt
        if ("refine".equals(request.getMode())) {
            // 内容优化模式：结合原内容和优化指令
            if (request.getContent() != null && !request.getContent().isEmpty()) {
                prompt.append("请根据以下指令优化内容：\n\n");
                prompt.append("指令：").append(request.getPrompt()).append("\n\n");
                prompt.append("原文：\n").append(request.getContent()).append("\n");
            }
        } else {
            // 自由模式或模板模式：直接使用prompt
            if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
                prompt.append(request.getPrompt());
            }
        }
        
        // 添加其他参数作为补充要求
        if (request.getLanguage() != null) {
            prompt.append("\n语言：").append(request.getLanguage());
        }
        if (request.getTone() != null) {
            prompt.append("\n语气：").append(request.getTone());
        }
        if (request.getStyle() != null) {
            prompt.append("\n风格：").append(request.getStyle());
        }
        if (request.getLength() != null) {
            prompt.append("\n期望长度：约").append(request.getLength()).append("字");
        }
        
        return prompt.toString();
    }
}
