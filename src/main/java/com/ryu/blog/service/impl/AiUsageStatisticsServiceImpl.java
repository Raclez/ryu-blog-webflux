package com.ryu.blog.service.impl;

import com.ryu.blog.dto.AiUsageStatistics;
import com.ryu.blog.entity.AiUsageQuota;
import com.ryu.blog.repository.AiGenerationHistoryRepository;
import com.ryu.blog.service.AiUsageStatisticsService;
import com.ryu.blog.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * AI使用统计服务实现
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageStatisticsServiceImpl implements AiUsageStatisticsService {

    private final AiGenerationHistoryRepository historyRepository;
    private final RateLimitService rateLimitService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String REDIS_STATS_PREFIX = "ai:stats:";
    
    // 模型定价（每1000个令牌的成本，美元）
    private static final Map<String, ModelPricing> MODEL_PRICING = new HashMap<>();
    
    static {
        // OpenAI定价
        MODEL_PRICING.put("gpt-4", new ModelPricing(0.03, 0.06));
        MODEL_PRICING.put("gpt-4-turbo", new ModelPricing(0.01, 0.03));
        MODEL_PRICING.put("gpt-3.5-turbo", new ModelPricing(0.0005, 0.0015));
        
        // Anthropic定价
        MODEL_PRICING.put("claude-3-opus", new ModelPricing(0.015, 0.075));
        MODEL_PRICING.put("claude-3-sonnet", new ModelPricing(0.003, 0.015));
        MODEL_PRICING.put("claude-3-haiku", new ModelPricing(0.00025, 0.00125));
        
        // 默认定价
        MODEL_PRICING.put("default", new ModelPricing(0.001, 0.002));
    }

    @Override
    public Mono<Void> recordUsage(Long userId, String providerName, String modelName,
                                  Integer tokenCount, Double cost, Long responseTime, Boolean success) {
        log.debug("记录使用情况: userId={}, provider={}, model={}, tokens={}, cost={}, time={}ms, success={}",
                userId, providerName, modelName, tokenCount, cost, responseTime, success);
        
        String key = buildStatsKey(userId, LocalDateTime.now());
        
        return redisTemplate.opsForHash()
                .increment(key, "totalRequests", 1)
                .then(redisTemplate.opsForHash().increment(key, success ? "successCount" : "failureCount", 1))
                .then(redisTemplate.opsForHash().increment(key, "totalTokens", tokenCount != null ? tokenCount : 0))
                .then(redisTemplate.opsForHash().increment(key, "totalCost", cost != null ? cost.longValue() * 1000000 : 0))
                .then(redisTemplate.opsForHash().increment(key, "totalResponseTime", responseTime != null ? responseTime : 0))
                .then(redisTemplate.expire(key, Duration.ofDays(31)))
                .then()
                .doOnSuccess(v -> log.debug("使用情况记录成功: userId={}", userId))
                .doOnError(error -> log.error("使用情况记录失败: userId={}", userId, error))
                .onErrorResume(error -> Mono.empty());
    }

    @Override
    public Mono<AiUsageStatistics> getHourlyStatistics(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfHour = now.withMinute(0).withSecond(0).withNano(0);
        return getStatistics(userId, startOfHour, now);
    }

    @Override
    public Mono<AiUsageStatistics> getDailyStatistics(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        return getStatistics(userId, startOfDay, now);
    }

    @Override
    public Mono<AiUsageStatistics> getMonthlyStatistics(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        return getStatistics(userId, startOfMonth, now);
    }

    /**
     * 获取今日统计信息
     */
    public Mono<AiUsageStatistics> getTodayStatistics(Long userId) {
        return getDailyStatistics(userId);
    }

    /**
     * 获取本月统计信息
     */
    public Mono<AiUsageStatistics> getMonthStatistics(Long userId) {
        return getMonthlyStatistics(userId);
    }

    /**
     * 获取统计信息（使用LocalDate）
     */
    public Mono<AiUsageStatistics> getStatistics(Long userId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(23, 59, 59);
        return getStatistics(userId, startTime, endTime);
    }

    @Override
    public Mono<AiUsageStatistics> getStatistics(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("获取统计信息: userId={}, start={}, end={}", userId, startTime, endTime);
        
        return historyRepository.findByUserIdAndCreateTimeBetween(userId, startTime, endTime, 0)
                .collectList()
                .zipWith(rateLimitService.getQuota(userId))
                .map(tuple -> {
                    var histories = tuple.getT1();
                    AiUsageQuota quota = tuple.getT2();
                    
                    // 计算统计数据
                    int totalRequests = histories.size();
                    long totalTokens = histories.stream()
                            .mapToLong(h -> h.getTokenCount() != null ? h.getTokenCount() : 0)
                            .sum();
                    double totalCost = histories.stream()
                            .mapToDouble(h -> h.getCost() != null ? h.getCost() : 0.0)
                            .sum();
                    long totalResponseTime = histories.stream()
                            .mapToLong(h -> h.getGenerationTime() != null ? h.getGenerationTime() : 0)
                            .sum();
                    long averageResponseTime = totalRequests > 0 ? totalResponseTime / totalRequests : 0;
                    
                    // 构建配额使用情况
                    AiUsageStatistics.QuotaUsage hourlyUsage = buildQuotaUsage(
                            quota.getHourlyUsed(), quota.getHourlyLimit());
                    AiUsageStatistics.QuotaUsage dailyUsage = buildQuotaUsage(
                            quota.getDailyUsed(), quota.getDailyLimit());
                    AiUsageStatistics.QuotaUsage monthlyUsage = buildQuotaUsage(
                            quota.getMonthlyUsed(), quota.getMonthlyLimit());
                    
                    return AiUsageStatistics.builder()
                            .userId(userId)
                            .startTime(startTime)
                            .endTime(endTime)
                            .totalRequests(totalRequests)
                            .successCount(totalRequests) // 简化处理，实际应从历史记录中统计
                            .failureCount(0)
                            .totalTokens(totalTokens)
                            .totalCost(totalCost)
                            .averageResponseTime(averageResponseTime)
                            .hourlyUsage(hourlyUsage)
                            .dailyUsage(dailyUsage)
                            .monthlyUsage(monthlyUsage)
                            .build();
                })
                .doOnSuccess(stats -> log.debug("统计信息获取成功: userId={}, requests={}, tokens={}, cost={}",
                        userId, stats.getTotalRequests(), stats.getTotalTokens(), stats.getTotalCost()))
                .doOnError(error -> log.error("统计信息获取失败: userId={}", userId, error));
    }

    @Override
    public Double estimateCost(String providerName, String modelName, Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null || completionTokens == null) {
            return 0.0;
        }
        
        // 查找模型定价
        ModelPricing pricing = MODEL_PRICING.getOrDefault(modelName, MODEL_PRICING.get("default"));
        
        // 计算成本（每1000个令牌）
        double promptCost = (promptTokens / 1000.0) * pricing.promptPrice;
        double completionCost = (completionTokens / 1000.0) * pricing.completionPrice;
        
        double totalCost = promptCost + completionCost;
        
        log.debug("成本估算: provider={}, model={}, promptTokens={}, completionTokens={}, cost=${}",
                providerName, modelName, promptTokens, completionTokens, totalCost);
        
        return totalCost;
    }

    @Override
    public Mono<String> generateReport(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("生成使用报告: userId={}, start={}, end={}", userId, startTime, endTime);
        
        return getStatistics(userId, startTime, endTime)
                .map(stats -> {
                    StringBuilder report = new StringBuilder();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    
                    report.append("# AI使用报告\n\n");
                    report.append("## 基本信息\n\n");
                    report.append(String.format("- **用户ID**: %d\n", stats.getUserId()));
                    report.append(String.format("- **统计时间**: %s 至 %s\n", 
                            stats.getStartTime().format(formatter), 
                            stats.getEndTime().format(formatter)));
                    report.append("\n");
                    
                    report.append("## 使用统计\n\n");
                    report.append(String.format("- **总请求次数**: %d\n", stats.getTotalRequests()));
                    report.append(String.format("- **成功次数**: %d\n", stats.getSuccessCount()));
                    report.append(String.format("- **失败次数**: %d\n", stats.getFailureCount()));
                    report.append(String.format("- **总令牌数**: %d\n", stats.getTotalTokens()));
                    report.append(String.format("- **平均响应时间**: %d ms\n", stats.getAverageResponseTime()));
                    report.append("\n");
                    
                    report.append("## 成本统计\n\n");
                    report.append(String.format("- **总成本**: $%.4f\n", stats.getTotalCost()));
                    if (stats.getTotalRequests() > 0) {
                        double avgCost = stats.getTotalCost() / stats.getTotalRequests();
                        report.append(String.format("- **平均每次成本**: $%.4f\n", avgCost));
                    }
                    report.append("\n");
                    
                    report.append("## 配额使用情况\n\n");
                    report.append("### 小时配额\n");
                    report.append(formatQuotaUsage(stats.getHourlyUsage()));
                    report.append("\n### 每日配额\n");
                    report.append(formatQuotaUsage(stats.getDailyUsage()));
                    report.append("\n### 每月配额\n");
                    report.append(formatQuotaUsage(stats.getMonthlyUsage()));
                    
                    return report.toString();
                })
                .doOnSuccess(report -> log.debug("使用报告生成成功: userId={}", userId))
                .doOnError(error -> log.error("使用报告生成失败: userId={}", userId, error));
    }

    /**
     * 构建配额使用情况
     */
    private AiUsageStatistics.QuotaUsage buildQuotaUsage(Integer used, Integer limit) {
        int remaining = Math.max(0, limit - used);
        double percentage = limit > 0 ? (used * 100.0 / limit) : 0.0;
        
        return AiUsageStatistics.QuotaUsage.builder()
                .used(used)
                .limit(limit)
                .remaining(remaining)
                .usagePercentage(percentage)
                .build();
    }

    /**
     * 格式化配额使用情况
     */
    private String formatQuotaUsage(AiUsageStatistics.QuotaUsage usage) {
        if (usage == null) {
            return "- 无数据\n";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("- **已使用**: %d / %d\n", usage.getUsed(), usage.getLimit()));
        sb.append(String.format("- **剩余**: %d\n", usage.getRemaining()));
        sb.append(String.format("- **使用率**: %.2f%%\n", usage.getUsagePercentage()));
        
        return sb.toString();
    }

    /**
     * 构建统计键
     */
    private String buildStatsKey(Long userId, LocalDateTime time) {
        String date = time.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return REDIS_STATS_PREFIX + userId + ":" + date;
    }

    /**
     * 模型定价
     */
    private static class ModelPricing {
        final double promptPrice;      // 每1000个提示词令牌的价格
        final double completionPrice;  // 每1000个完成令牌的价格

        ModelPricing(double promptPrice, double completionPrice) {
            this.promptPrice = promptPrice;
            this.completionPrice = completionPrice;
        }
    }
}
