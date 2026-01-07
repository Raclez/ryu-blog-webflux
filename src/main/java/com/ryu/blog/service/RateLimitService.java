package com.ryu.blog.service;

import com.ryu.blog.entity.AiUsageQuota;
import reactor.core.publisher.Mono;

/**
 * 速率限制服务接口
 * 
 * <p>提供AI使用配额管理和速率限制功能。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface RateLimitService {

    /**
     * 检查用户是否超过速率限制
     * 
     * <p>检查小时、天、月三个维度的配额，任一超限则抛出异常。
     * 
     * @param userId 用户ID
     * @return 配额信息
     * @throws com.ryu.blog.exception.RateLimitExceededException 超过限制时抛出
     */
    Mono<AiUsageQuota> checkLimit(Long userId);

    /**
     * 增加用户使用次数
     * 
     * <p>在检查通过后调用，增加各维度的使用计数。
     * 
     * @param userId 用户ID
     * @return 更新后的配额信息
     */
    Mono<AiUsageQuota> incrementUsage(Long userId);

    /**
     * 检查并增加使用次数（原子操作）
     * 
     * <p>先检查限制，通过后增加使用次数。
     * 
     * @param userId 用户ID
     * @return 更新后的配额信息
     * @throws com.ryu.blog.exception.RateLimitExceededException 超过限制时抛出
     */
    Mono<AiUsageQuota> checkAndIncrement(Long userId);

    /**
     * 获取用户配额信息
     * 
     * <p>如果用户没有配额记录，则创建默认配额。
     * 
     * @param userId 用户ID
     * @return 配额信息
     */
    Mono<AiUsageQuota> getQuota(Long userId);

    /**
     * 创建或更新用户配额
     * 
     * @param userId 用户ID
     * @param hourlyLimit 每小时限制
     * @param dailyLimit 每日限制
     * @param monthlyLimit 每月限制
     * @return 配额信息
     */
    Mono<AiUsageQuota> setQuota(Long userId, Integer hourlyLimit, Integer dailyLimit, Integer monthlyLimit);

    /**
     * 重置用户配额（手动重置）
     * 
     * @param userId 用户ID
     * @return 重置后的配额信息
     */
    Mono<AiUsageQuota> resetQuota(Long userId);

    /**
     * 检查并重置过期的配额
     * 
     * <p>自动检查小时、天、月配额是否需要重置。
     * 
     * @param quota 配额信息
     * @return 是否进行了重置
     */
    Mono<Boolean> checkAndResetExpired(AiUsageQuota quota);

    /**
     * 获取用户剩余配额
     * 
     * @param userId 用户ID
     * @return 剩余配额信息（小时、天、月）
     */
    Mono<QuotaRemaining> getRemainingQuota(Long userId);

    /**
     * 剩余配额信息
     */
    class QuotaRemaining {
        private final Integer hourlyRemaining;
        private final Integer dailyRemaining;
        private final Integer monthlyRemaining;

        public QuotaRemaining(Integer hourlyRemaining, Integer dailyRemaining, Integer monthlyRemaining) {
            this.hourlyRemaining = hourlyRemaining;
            this.dailyRemaining = dailyRemaining;
            this.monthlyRemaining = monthlyRemaining;
        }

        public Integer getHourlyRemaining() {
            return hourlyRemaining;
        }

        public Integer getDailyRemaining() {
            return dailyRemaining;
        }

        public Integer getMonthlyRemaining() {
            return monthlyRemaining;
        }
    }
}
