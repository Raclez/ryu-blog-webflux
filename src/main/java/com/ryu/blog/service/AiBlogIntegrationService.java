package com.ryu.blog.service;

import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.entity.Posts;
import reactor.core.publisher.Mono;

/**
 * AI博客集成服务接口
 * 
 * <p>提供AI生成内容与博客系统的集成功能。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface AiBlogIntegrationService {

    /**
     * 将AI生成的内容保存为博客草稿
     * 
     * @param result AI生成结果
     * @param userId 用户ID
     * @return 创建的文章草稿
     */
    Mono<Posts> saveToDraft(AiGenerationResult result, Long userId);

    /**
     * 将AI生成的内容保存为博客草稿（带分类和标签）
     * 
     * @param result AI生成结果
     * @param userId 用户ID
     * @param categoryIds 分类ID列表
     * @param tagIds 标签ID列表
     * @return 创建的文章草稿
     */
    Mono<Posts> saveToDraft(AiGenerationResult result, Long userId, 
                             java.util.List<Long> categoryIds, java.util.List<Long> tagIds);

    /**
     * 从生成结果中提取标题
     * 
     * @param content 内容
     * @return 标题
     */
    String extractTitle(String content);

    /**
     * 从生成结果中提取摘要
     * 
     * @param content 内容
     * @param maxLength 最大长度
     * @return 摘要
     */
    String extractSummary(String content, int maxLength);
}
