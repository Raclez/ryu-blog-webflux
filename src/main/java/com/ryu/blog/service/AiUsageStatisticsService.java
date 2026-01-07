package com.ryu.blog.service;

import com.ryu.blog.dto.AiUsageStatistics;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * AI使用统计服务接口
 * 
 * <p>提供使用统计、成本追踪和报告生成功能。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface AiUsageStatisticsService {

    /**
     * 记录使用情况
     * 
     * @param userId 用户ID
     * @param providerName 提供商名称
     * @param modelName 模型名称
     * @param tokenCount 令牌数
     * @param cost 成本
     * @param responseTime 响应时间（毫秒）
     * @param success 是否成功
     * @return 完成信号
     */
    Mono<Void> recordUsage(Long userId, String providerName, String modelName, 
                          Integer tokenCount, Double cost, Long responseTime, Boolean success);

    /**
     * 获取用户统计信息（当前小时）
     * 
     * @param userId 用户ID
     * @return 统计信息
     */
    Mono<AiUsageStatistics> getHourlyStatistics(Long userId);

    /**
     * 获取用户统计信息（当天）
     * 
     * @param userId 用户ID
     * @return 统计信息
     */
    Mono<AiUsageStatistics> getDailyStatistics(Long userId);

    /**
     * 获取用户统计信息（当月）
     * 
     * @param userId 用户ID
     * @return 统计信息
     */
    Mono<AiUsageStatistics> getMonthlyStatistics(Long userId);

    /**
     * 获取用户统计信息（自定义时间范围）
     * 
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计信息
     */
    Mono<AiUsageStatistics> getStatistics(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 估算令牌成本
     * 
     * @param providerName 提供商名称
     * @param modelName 模型名称
     * @param promptTokens 提示词令牌数
     * @param completionTokens 完成令牌数
     * @return 估算成本（美元）
     */
    Double estimateCost(String providerName, String modelName, Integer promptTokens, Integer completionTokens);

    /**
     * 生成使用报告
     * 
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 报告内容（Markdown格式）
     */
    Mono<String> generateReport(Long userId, LocalDateTime startTime, LocalDateTime endTime);
}
