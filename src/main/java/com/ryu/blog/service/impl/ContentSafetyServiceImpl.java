package com.ryu.blog.service.impl;

import com.ryu.blog.service.ContentSafetyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内容安全服务实现
 * 
 * <p>使用关键词过滤实现基础的内容安全检查。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSafetyServiceImpl implements ContentSafetyService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String KEYWORDS_KEY = "ai:safety:keywords";

    // 默认敏感词列表
    private static final List<String> DEFAULT_KEYWORDS = Arrays.asList(
            "暴力", "色情", "赌博", "毒品", "恐怖", "政治敏感",
            "违法", "犯罪", "欺诈", "诈骗"
    );

    @Override
    public Mono<Boolean> isSafe(String content) {
        return check(content).map(SafetyCheckResult::isSafe);
    }

    @Override
    public Mono<SafetyCheckResult> check(String content) {
        log.debug("检查内容安全: length={}", content != null ? content.length() : 0);
        
        if (content == null || content.isEmpty()) {
            return Mono.just(new SafetyCheckResult(true, List.of(), "内容为空"));
        }
        
        return getKeywords()
                .map(keywords -> {
                    List<String> matched = new ArrayList<>();
                    
                    // 检查是否包含敏感词
                    for (String keyword : keywords) {
                        if (content.toLowerCase().contains(keyword.toLowerCase())) {
                            matched.add(keyword);
                        }
                    }
                    
                    boolean safe = matched.isEmpty();
                    String reason = safe ? "内容安全" : "包含敏感词: " + String.join(", ", matched);
                    
                    log.debug("内容安全检查完成: safe={}, matched={}", safe, matched.size());
                    
                    return new SafetyCheckResult(safe, matched, reason);
                })
                .doOnError(error -> log.error("内容安全检查失败", error))
                .onErrorReturn(new SafetyCheckResult(true, List.of(), "检查失败，默认通过"));
    }

    @Override
    public Mono<Boolean> addKeyword(String keyword) {
        log.info("添加敏感词: {}", keyword);
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        return redisTemplate.opsForSet()
                .add(KEYWORDS_KEY, keyword.trim().toLowerCase())
                .map(count -> count > 0)
                .doOnSuccess(result -> log.info("敏感词添加{}: {}", result ? "成功" : "失败", keyword))
                .doOnError(error -> log.error("添加敏感词失败: {}", keyword, error))
                .onErrorReturn(false);
    }

    @Override
    public Mono<Boolean> removeKeyword(String keyword) {
        log.info("删除敏感词: {}", keyword);
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        return redisTemplate.opsForSet()
                .remove(KEYWORDS_KEY, keyword.trim().toLowerCase())
                .map(count -> count > 0)
                .doOnSuccess(result -> log.info("敏感词删除{}: {}", result ? "成功" : "失败", keyword))
                .doOnError(error -> log.error("删除敏感词失败: {}", keyword, error))
                .onErrorReturn(false);
    }

    @Override
    public Mono<List<String>> getKeywords() {
        return redisTemplate.opsForSet()
                .members(KEYWORDS_KEY)
                .collect(Collectors.toList())
                .flatMap(keywords -> {
                    // 如果Redis中没有敏感词，初始化默认敏感词
                    if (keywords.isEmpty()) {
                        return initializeDefaultKeywords()
                                .thenReturn(DEFAULT_KEYWORDS);
                    }
                    return Mono.just(keywords);
                })
                .doOnSuccess(keywords -> log.debug("获取敏感词列表: count={}", keywords.size()))
                .doOnError(error -> log.error("获取敏感词列表失败", error))
                .onErrorReturn(DEFAULT_KEYWORDS);
    }

    /**
     * 初始化默认敏感词
     */
    private Mono<Void> initializeDefaultKeywords() {
        log.info("初始化默认敏感词: count={}", DEFAULT_KEYWORDS.size());
        
        return redisTemplate.opsForSet()
                .add(KEYWORDS_KEY, DEFAULT_KEYWORDS.stream()
                        .map(String::toLowerCase)
                        .toArray(String[]::new))
                .then()
                .doOnSuccess(v -> log.info("默认敏感词初始化成功"))
                .doOnError(error -> log.error("默认敏感词初始化失败", error))
                .onErrorResume(error -> Mono.empty());
    }
}
