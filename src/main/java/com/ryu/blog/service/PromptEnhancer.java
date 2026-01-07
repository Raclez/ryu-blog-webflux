package com.ryu.blog.service;

import com.ryu.blog.dto.AiGenerationRequest;
import reactor.core.publisher.Mono;

/**
 * 提示词增强器
 * 
 * <p>根据上下文信息优化和增强用户提供的提示词，以获得更好的生成效果。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface PromptEnhancer {

    /**
     * 增强提示词
     * 
     * <p>根据请求参数和博客上下文信息，增强用户提供的提示词。
     * 
     * @param request 生成请求
     * @return 增强后的提示词
     */
    Mono<String> enhance(AiGenerationRequest request);

    /**
     * 构建系统提示词
     * 
     * <p>构建系统级别的提示词，用于指导AI的行为和输出格式。
     * 
     * @param request 生成请求
     * @return 系统提示词
     */
    String buildSystemPrompt(AiGenerationRequest request);

    /**
     * 添加博客上下文
     * 
     * <p>添加博客相关的上下文信息，如现有分类、标签、写作风格等。
     * 
     * @param basePrompt 基础提示词
     * @param userId 用户ID
     * @return 包含上下文的提示词
     */
    Mono<String> addBlogContext(String basePrompt, Long userId);
}
