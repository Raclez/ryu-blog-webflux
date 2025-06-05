package com.ryu.blog.service;

import reactor.core.publisher.Mono;

/**
 * 内容处理服务接口
 * 
 * @author ryu
 */
public interface ContentService {

    /**
     * 将Markdown内容转换为HTML
     * 
     * @param markdown Markdown格式的内容
     * @return 转换后的HTML内容
     */
    Mono<String> markdownToHtml(String markdown);
    
    /**
     * 生成文章摘要
     * 
     * @param content 文章内容
     * @param maxLength 最大长度
     * @return 生成的摘要
     */
    Mono<String> generateExcerpt(String content, int maxLength);
    
    /**
     * 从HTML内容中提取纯文本
     * 
     * @param htmlContent HTML内容
     * @return 纯文本内容
     */
    Mono<String> extractTextFromHtml(String htmlContent);
    
    /**
     * 计算文章阅读时间（以分钟为单位）
     * 
     * @param content 文章内容
     * @return 预估的阅读时间（分钟）
     */
    Mono<Integer> calculateReadingTime(String content);
} 