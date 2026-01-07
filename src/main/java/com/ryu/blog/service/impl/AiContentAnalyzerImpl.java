package com.ryu.blog.service.impl;

import com.ryu.blog.entity.Category;
import com.ryu.blog.entity.Tag;
import com.ryu.blog.repository.CategoryRepository;
import com.ryu.blog.repository.TagRepository;
import com.ryu.blog.service.AiContentAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI内容分析服务实现
 * 
 * <p>使用关键词匹配和简单的NLP技术提取分类和标签。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContentAnalyzerImpl implements AiContentAnalyzer {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    // 分类关键词映射
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new HashMap<>();
    
    static {
        CATEGORY_KEYWORDS.put("技术", Arrays.asList("编程", "代码", "开发", "算法", "数据结构", "框架", "API", "数据库"));
        CATEGORY_KEYWORDS.put("前端", Arrays.asList("HTML", "CSS", "JavaScript", "React", "Vue", "Angular", "前端"));
        CATEGORY_KEYWORDS.put("后端", Arrays.asList("Java", "Python", "Node.js", "Spring", "后端", "服务器", "微服务"));
        CATEGORY_KEYWORDS.put("数据库", Arrays.asList("MySQL", "PostgreSQL", "MongoDB", "Redis", "SQL", "NoSQL"));
        CATEGORY_KEYWORDS.put("运维", Arrays.asList("Docker", "Kubernetes", "CI/CD", "部署", "运维", "DevOps"));
        CATEGORY_KEYWORDS.put("人工智能", Arrays.asList("AI", "机器学习", "深度学习", "神经网络", "NLP", "计算机视觉"));
        CATEGORY_KEYWORDS.put("生活", Arrays.asList("生活", "日常", "随笔", "感悟", "心情"));
        CATEGORY_KEYWORDS.put("教程", Arrays.asList("教程", "指南", "入门", "学习", "如何"));
    }

    @Override
    public Mono<List<String>> extractCategories(String content) {
        log.debug("从内容中提取分类: length={}", content != null ? content.length() : 0);
        
        if (content == null || content.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        
        return Mono.fromCallable(() -> {
            Map<String, Integer> categoryScores = new HashMap<>();
            String lowerContent = content.toLowerCase();
            
            // 计算每个分类的匹配分数
            for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
                String category = entry.getKey();
                List<String> keywords = entry.getValue();
                
                int score = 0;
                for (String keyword : keywords) {
                    if (lowerContent.contains(keyword.toLowerCase())) {
                        score++;
                    }
                }
                
                if (score > 0) {
                    categoryScores.put(category, score);
                }
            }
            
            // 按分数排序，返回前3个
            List<String> categories = categoryScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            log.debug("提取的分类: {}", categories);
            return categories;
        });
    }

    @Override
    public Mono<List<String>> extractTags(String content) {
        log.debug("从内容中提取标签: length={}", content != null ? content.length() : 0);
        
        if (content == null || content.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        
        return Mono.fromCallable(() -> {
            Set<String> tags = new HashSet<>();
            
            // 提取技术关键词作为标签
            List<String> techKeywords = Arrays.asList(
                    "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust",
                    "Spring", "React", "Vue", "Angular", "Node.js",
                    "MySQL", "PostgreSQL", "MongoDB", "Redis",
                    "Docker", "Kubernetes", "AWS", "Azure",
                    "AI", "机器学习", "深度学习"
            );
            
            for (String keyword : techKeywords) {
                if (content.contains(keyword)) {
                    tags.add(keyword);
                }
            }
            
            // 提取中文关键词（简单实现：提取2-4字的高频词）
            Pattern pattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}");
            Matcher matcher = pattern.matcher(content);
            Map<String, Integer> wordFreq = new HashMap<>();
            
            while (matcher.find()) {
                String word = matcher.group();
                wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
            }
            
            // 选择频率最高的词作为标签
            wordFreq.entrySet().stream()
                    .filter(e -> e.getValue() >= 2) // 至少出现2次
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> tags.add(e.getKey()));
            
            List<String> tagList = new ArrayList<>(tags);
            log.debug("提取的标签: {}", tagList);
            return tagList.stream().limit(10).collect(Collectors.toList());
        });
    }

    @Override
    public Mono<List<Long>> matchExistingCategories(List<String> suggestedCategories) {
        log.debug("匹配现有分类: {}", suggestedCategories);
        
        if (suggestedCategories == null || suggestedCategories.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        
        return Flux.fromIterable(suggestedCategories)
                .flatMap(categoryName -> 
                    categoryRepository.findByNameAndIsDeleted(categoryName, 0)
                            .map(Category::getId)
                            .onErrorResume(error -> {
                                log.debug("分类不存在: {}", categoryName);
                                return Mono.empty();
                            })
                )
                .collectList()
                .doOnSuccess(ids -> log.debug("匹配到的分类ID: {}", ids));
    }

    @Override
    public Mono<List<Long>> matchExistingTags(List<String> suggestedTags) {
        log.debug("匹配现有标签: {}", suggestedTags);
        
        if (suggestedTags == null || suggestedTags.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        
        return Flux.fromIterable(suggestedTags)
                .flatMap(tagName -> 
                    tagRepository.findByNameAndIsDeleted(tagName, 0)
                            .map(Tag::getId)
                            .onErrorResume(error -> {
                                log.debug("标签不存在: {}", tagName);
                                return Mono.empty();
                            })
                )
                .collectList()
                .doOnSuccess(ids -> log.debug("匹配到的标签ID: {}", ids));
    }

    @Override
    public Mono<ContentAnalysisResult> analyzeContent(String content) {
        log.debug("分析内容: length={}", content != null ? content.length() : 0);
        
        return extractCategories(content)
                .zipWith(extractTags(content))
                .flatMap(tuple -> {
                    List<String> categories = tuple.getT1();
                    List<String> tags = tuple.getT2();
                    
                    return matchExistingCategories(categories)
                            .zipWith(matchExistingTags(tags))
                            .map(matchTuple -> new ContentAnalysisResult(
                                    categories,
                                    tags,
                                    matchTuple.getT1(),
                                    matchTuple.getT2()
                            ));
                })
                .doOnSuccess(result -> log.debug("内容分析完成: categories={}, tags={}", 
                        result.getSuggestedCategories().size(), 
                        result.getSuggestedTags().size()))
                .doOnError(error -> log.error("内容分析失败", error));
    }
}
