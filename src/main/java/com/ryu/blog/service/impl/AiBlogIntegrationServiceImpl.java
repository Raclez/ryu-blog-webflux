package com.ryu.blog.service.impl;

import com.ryu.blog.constant.SystemConstants;
import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.service.AiBlogIntegrationService;
import com.ryu.blog.service.AiContentAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI博客集成服务实现
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBlogIntegrationServiceImpl implements AiBlogIntegrationService {

    private final PostsRepository postsRepository;
    private final AiContentAnalyzer contentAnalyzer;

    @Override
    public Mono<Posts> saveToDraft(AiGenerationResult result, Long userId) {
        log.info("保存AI生成内容为草稿: userId={}", userId);
        
        // 分析内容，提取分类和标签
        return contentAnalyzer.analyzeContent(result.getContent())
                .flatMap(analysis -> saveToDraft(
                        result, 
                        userId, 
                        analysis.getMatchedCategoryIds(), 
                        analysis.getMatchedTagIds()
                ));
    }

    @Override
    public Mono<Posts> saveToDraft(AiGenerationResult result, Long userId, 
                                     List<Long> categoryIds, List<Long> tagIds) {
        log.info("保存AI生成内容为草稿: userId={}, categories={}, tags={}", 
                userId, categoryIds.size(), tagIds.size());
        
        String content = result.getContent();
        String title = extractTitle(content);
        String summary = extractSummary(content, 200);
        
        LocalDateTime now = LocalDateTime.now();
        
        Posts post = new Posts();
        post.setUserId(userId);
        post.setTitle(title);
        post.setContent(content);
        post.setExcerpt(summary);
        post.setStatus(Posts.Status.DRAFT); // 草稿状态
        post.setViews(0);
        post.setIsOriginal(true);
        post.setAllowComment(true);
        post.setVisibility(Posts.Visibility.PUBLIC);
        post.setCreateTime(now);
        post.setUpdateTime(now);
        
        return postsRepository.save(post)
                .flatMap(savedPost -> {
                    // 关联分类和标签
                    return associateCategoriesAndTags(savedPost.getId(), categoryIds, tagIds)
                            .thenReturn(savedPost);
                })
                .doOnSuccess(savedPost -> log.info("草稿保存成功: postId={}, title={}", 
                        savedPost.getId(), savedPost.getTitle()))
                .doOnError(error -> log.error("草稿保存失败: userId={}", userId, error));
    }

    @Override
    public String extractTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "AI生成的文章";
        }
        
        // 尝试提取第一行作为标题
        String[] lines = content.split("\n");
        if (lines.length > 0) {
            String firstLine = lines[0].trim();
            
            // 移除Markdown标题标记
            firstLine = firstLine.replaceAll("^#+\\s*", "");
            
            // 限制标题长度
            if (firstLine.length() > 100) {
                firstLine = firstLine.substring(0, 100) + "...";
            }
            
            if (!firstLine.isEmpty()) {
                return firstLine;
            }
        }
        
        // 如果第一行为空，尝试提取前50个字符
        String title = content.trim();
        if (title.length() > 50) {
            title = title.substring(0, 50) + "...";
        }
        
        return title.isEmpty() ? "AI生成的文章" : title;
    }

    @Override
    public String extractSummary(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // 移除Markdown标记
        String plainText = content
                .replaceAll("#+\\s*", "")           // 移除标题标记
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")  // 移除粗体
                .replaceAll("\\*(.+?)\\*", "$1")        // 移除斜体
                .replaceAll("```[\\s\\S]*?```", "")     // 移除代码块
                .replaceAll("`(.+?)`", "$1")            // 移除行内代码
                .replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1") // 移除链接
                .trim();
        
        // 提取前几段作为摘要
        String[] paragraphs = plainText.split("\n\n");
        StringBuilder summary = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (!paragraph.isEmpty()) {
                summary.append(paragraph).append(" ");
                if (summary.length() >= maxLength) {
                    break;
                }
            }
        }
        
        String result = summary.toString().trim();
        
        // 限制长度
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength) + "...";
        }
        
        return result;
    }

    /**
     * 关联分类和标签
     */
    private Mono<Void> associateCategoriesAndTags(Long postId, List<Long> categoryIds, List<Long> tagIds) {
        log.debug("关联分类和标签: postId={}, categories={}, tags={}", 
                postId, categoryIds.size(), tagIds.size());
        
        // TODO: 实现分类和标签的关联逻辑
        // 这里需要根据实际的数据库表结构来实现
        // 通常需要向 post_category 和 post_tag 关联表插入数据
        
        return Mono.empty();
    }
}
