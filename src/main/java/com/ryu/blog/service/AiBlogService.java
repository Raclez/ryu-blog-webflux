package com.ryu.blog.service;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.entity.AiGenerationHistory;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * AI博客服务接口
 * 
 * <p>提供AI驱动的博客内容生成、优化和管理功能。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface AiBlogService {

    /**
     * 生成博客内容（非流式）
     * 
     * <p>根据请求参数生成完整的博客内容，包括标题、正文、摘要等。
     * 
     * @param request 生成请求
     * @return 生成结果
     */
    Mono<AiGenerationResult> generateBlogContent(AiGenerationRequest request);

    /**
     * 流式生成博客内容
     * 
     * <p>实时流式返回生成的内容，适用于需要即时反馈的场景。
     * 
     * @param request 生成请求
     * @return 内容流
     */
    Flux<String> generateBlogContentStream(AiGenerationRequest request);

    /**
     * 优化现有内容
     * 
     * <p>对现有博客内容进行优化，支持扩展、摘要、重写、翻译等操作。
     * 
     * @param request 优化请求（包含现有内容和优化指令）
     * @return 优化后的结果
     */
    Mono<AiGenerationResult> refineContent(AiGenerationRequest request);

    /**
     * 使用模板生成内容
     * 
     * <p>基于预定义的模板生成内容，模板变量会被替换为实际值。
     * 
     * @param templateId 模板ID
     * @param variables 模板变量映射
     * @param userId 用户ID
     * @return 生成结果
     */
    Mono<AiGenerationResult> generateWithTemplate(Long templateId, Map<String, String> variables, Long userId);

    /**
     * 获取生成历史
     * 
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 历史记录列表
     */
    Flux<AiGenerationHistory> getGenerationHistory(Long userId, Pageable pageable);

    /**
     * 根据ID获取历史记录
     * 
     * @param id 历史记录ID
     * @param userId 用户ID（用于权限检查）
     * @return 历史记录
     */
    Mono<AiGenerationHistory> getHistoryById(Long id, Long userId);

    /**
     * 删除历史记录
     * 
     * @param id 历史记录ID
     * @param userId 用户ID（用于权限检查）
     * @return 是否删除成功
     */
    Mono<Boolean> deleteHistory(Long id, Long userId);

    /**
     * 重新生成内容
     * 
     * <p>基于历史记录重新生成内容。
     * 
     * @param historyId 历史记录ID
     * @param userId 用户ID
     * @return 生成结果
     */
    Mono<AiGenerationResult> regenerate(Long historyId, Long userId);

    /**
     * 统计用户生成次数
     * 
     * @param userId 用户ID
     * @return 生成次数
     */
    Mono<Long> countGenerations(Long userId);
}
