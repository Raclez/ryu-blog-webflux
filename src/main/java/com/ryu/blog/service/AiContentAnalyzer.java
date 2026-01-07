package com.ryu.blog.service;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * AI内容分析服务接口
 * 
 * <p>提供内容分析功能，包括分类和标签提取。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface AiContentAnalyzer {

    /**
     * 从内容中提取分类建议
     * 
     * @param content 内容
     * @return 分类建议列表
     */
    Mono<List<String>> extractCategories(String content);

    /**
     * 从内容中提取标签建议
     * 
     * @param content 内容
     * @return 标签建议列表
     */
    Mono<List<String>> extractTags(String content);

    /**
     * 匹配现有的分类
     * 
     * @param suggestedCategories 建议的分类
     * @return 匹配的分类ID列表
     */
    Mono<List<Long>> matchExistingCategories(List<String> suggestedCategories);

    /**
     * 匹配现有的标签
     * 
     * @param suggestedTags 建议的标签
     * @return 匹配的标签ID列表
     */
    Mono<List<Long>> matchExistingTags(List<String> suggestedTags);

    /**
     * 分析内容并提取分类和标签
     * 
     * @param content 内容
     * @return 分析结果
     */
    Mono<ContentAnalysisResult> analyzeContent(String content);

    /**
     * 内容分析结果
     */
    class ContentAnalysisResult {
        private final List<String> suggestedCategories;
        private final List<String> suggestedTags;
        private final List<Long> matchedCategoryIds;
        private final List<Long> matchedTagIds;

        public ContentAnalysisResult(List<String> suggestedCategories, List<String> suggestedTags,
                                    List<Long> matchedCategoryIds, List<Long> matchedTagIds) {
            this.suggestedCategories = suggestedCategories;
            this.suggestedTags = suggestedTags;
            this.matchedCategoryIds = matchedCategoryIds;
            this.matchedTagIds = matchedTagIds;
        }

        public List<String> getSuggestedCategories() {
            return suggestedCategories;
        }

        public List<String> getSuggestedTags() {
            return suggestedTags;
        }

        public List<Long> getMatchedCategoryIds() {
            return matchedCategoryIds;
        }

        public List<Long> getMatchedTagIds() {
            return matchedTagIds;
        }
    }
}
