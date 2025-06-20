package com.ryu.blog.service.impl;

import com.ryu.blog.utils.MarkdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.regex.Pattern;
import com.ryu.blog.service.ContentService;

/**
 * 内容处理服务实现类
 * 
 * @author ryu
 */
@Slf4j
@Service
public class ContentServiceImpl implements ContentService {

    private static final int WORDS_PER_MINUTE = 250; // 每分钟阅读词数
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    
    @Override
    public Mono<String> markdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return Mono.just("");
        }
        
        // 使用MarkdownUtils工具类
        return MarkdownUtils.renderHtmlReactive(markdown)
            .doOnError(e -> log.error("Markdown转HTML失败: {}", e.getMessage()));
    }
    
    @Override
    public Mono<String> generateExcerpt(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return Mono.just("");
        }
        
        int finalMaxLength = maxLength > 0 ? maxLength : 200; // 默认摘要长度
        
        return extractTextFromHtml(content)
            .map(text -> {
                if (text.length() <= finalMaxLength) {
                    return text;
                }
                
                // 寻找句号或其他分隔符作为自然断句点
                int endIdx = text.substring(0, finalMaxLength).lastIndexOf('。');
                if (endIdx == -1) {
                    endIdx = text.substring(0, finalMaxLength).lastIndexOf('.');
                }
                if (endIdx == -1) {
                    endIdx = text.substring(0, finalMaxLength).lastIndexOf('!');
                }
                if (endIdx == -1) {
                    endIdx = text.substring(0, finalMaxLength).lastIndexOf('?');
                }
                
                // 如果找不到自然断句点，就按最大长度截断
                if (endIdx == -1 || endIdx < finalMaxLength / 2) {
                    return text.substring(0, finalMaxLength) + "...";
                }
                
                return text.substring(0, endIdx + 1) + "...";
            })
            .doOnError(e -> log.error("生成文章摘要失败: {}", e.getMessage()));
    }
    
    @Override
    public Mono<String> extractTextFromHtml(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return Mono.just("");
        }
        
        return Mono.fromCallable(() -> {
                // 使用正则表达式去除HTML标签
                String text = HTML_TAG_PATTERN.matcher(htmlContent).replaceAll("");
                // 去除多余空白字符
                return text.replaceAll("\\s+", " ").trim();
            })
            .subscribeOn(Schedulers.parallel())
            .doOnError(e -> log.error("从HTML提取文本失败: {}", e.getMessage()));
    }
    
    @Override
    public Mono<Integer> calculateReadingTime(String content) {
        if (content == null || content.isEmpty()) {
            return Mono.just(1); // 最小阅读时间为1分钟
        }
        
        // 使用MarkdownUtils工具类
        return MarkdownUtils.calculateReadingTimeReactive(content)
            .doOnError(e -> log.error("计算阅读时间失败: {}", e.getMessage()))
            .onErrorReturn(1); // 发生错误时返回默认值1
    }
} 