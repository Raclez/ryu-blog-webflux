package com.ryu.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI使用统计DTO
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageStatistics {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 统计时间范围开始
     */
    private LocalDateTime startTime;

    /**
     * 统计时间范围结束
     */
    private LocalDateTime endTime;

    /**
     * 总请求次数
     */
    private Integer totalRequests;

    /**
     * 成功次数
     */
    private Integer successCount;

    /**
     * 失败次数
     */
    private Integer failureCount;

    /**
     * 总令牌使用量
     */
    private Long totalTokens;

    /**
     * 提示词令牌数
     */
    private Long promptTokens;

    /**
     * 完成令牌数
     */
    private Long completionTokens;

    /**
     * 估算总成本（美元）
     */
    private Double totalCost;

    /**
     * 平均响应时间（毫秒）
     */
    private Long averageResponseTime;

    /**
     * 最常用的提供商
     */
    private String mostUsedProvider;

    /**
     * 最常用的模型
     */
    private String mostUsedModel;

    /**
     * 小时配额使用情况
     */
    private QuotaUsage hourlyUsage;

    /**
     * 每日配额使用情况
     */
    private QuotaUsage dailyUsage;

    /**
     * 每月配额使用情况
     */
    private QuotaUsage monthlyUsage;

    /**
     * 配额使用情况
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaUsage {
        private Integer used;
        private Integer limit;
        private Integer remaining;
        private Double usagePercentage;
    }
}
