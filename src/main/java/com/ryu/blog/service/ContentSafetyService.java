package com.ryu.blog.service;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 内容安全服务接口
 * 
 * <p>提供内容安全检查和审核功能。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface ContentSafetyService {

    /**
     * 检查内容是否安全
     * 
     * @param content 待检查的内容
     * @return 是否安全
     */
    Mono<Boolean> isSafe(String content);

    /**
     * 检查内容并返回详细结果
     * 
     * @param content 待检查的内容
     * @return 检查结果
     */
    Mono<SafetyCheckResult> check(String content);

    /**
     * 添加敏感词
     * 
     * @param keyword 敏感词
     * @return 是否添加成功
     */
    Mono<Boolean> addKeyword(String keyword);

    /**
     * 删除敏感词
     * 
     * @param keyword 敏感词
     * @return 是否删除成功
     */
    Mono<Boolean> removeKeyword(String keyword);

    /**
     * 获取所有敏感词
     * 
     * @return 敏感词列表
     */
    Mono<List<String>> getKeywords();

    /**
     * 安全检查结果
     */
    class SafetyCheckResult {
        private final boolean safe;
        private final List<String> matchedKeywords;
        private final String reason;

        public SafetyCheckResult(boolean safe, List<String> matchedKeywords, String reason) {
            this.safe = safe;
            this.matchedKeywords = matchedKeywords;
            this.reason = reason;
        }

        public boolean isSafe() {
            return safe;
        }

        public List<String> getMatchedKeywords() {
            return matchedKeywords;
        }

        public String getReason() {
            return reason;
        }
    }
}
