package com.ryu.blog.enums;

import lombok.Getter;

/**
 * 任务类型枚举
 * 
 * @author ryu
 */
@Getter
public enum TaskType {
    /**
     * AI内容生成
     */
    AI_GENERATION("AI内容生成"),

    /**
     * 文章定时发布
     *
     * <p>到点由 {@code ScheduledTaskPromoter} 从延迟区搬进执行队列，
     * 再由 {@code ArticlePublishTaskHandler} 把文章翻成「已发布」。
     */
    ARTICLE_PUBLISH("文章定时发布");

    private final String description;
    
    TaskType(String description) {
        this.description = description;
    }
}
