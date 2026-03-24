package com.ryu.blog.service.impl;

import com.ryu.blog.entity.AiUsageQuota;
import com.ryu.blog.exception.RateLimitExceededException;
import com.ryu.blog.repository.AiUsageQuotaRepository;
import com.ryu.blog.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 速率限制服务实现
 * 
 * <p>使用Redis实现分布式速率限制，支持小时/天/月三个维度的配额管理。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private final AiUsageQuotaRepository quotaRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    // 默认配额
    private static final int DEFAULT_HOURLY_LIMIT = 10;
    private static final int DEFAULT_DAILY_LIMIT = 50;
    private static final int DEFAULT_MONTHLY_LIMIT = 1000;

    // Redis键前缀
    private static final String REDIS_KEY_PREFIX = "ai:quota:";

    @Override
    public Mono<AiUsageQuota> checkLimit(Long userId) {
        log.debug("检查用户速率限制: userId={}", userId);
        
        return getQuota(userId)
                .flatMap(this::checkAndResetExpiredQuota)
                .flatMap(quota -> {
                    // 检查小时限制
                    if (quota.getHourlyUsed() >= quota.getHourlyLimit()) {
                        return Mono.error(new RateLimitExceededException(
                                "每小时", quota.getHourlyLimit(), quota.getHourlyUsed()));
                    }
                    
                    // 检查每日限制
                    if (quota.getDailyUsed() >= quota.getDailyLimit()) {
                        return Mono.error(new RateLimitExceededException(
                                "每日", quota.getDailyLimit(), quota.getDailyUsed()));
                    }
                    
                    // 检查每月限制
                    if (quota.getMonthlyUsed() >= quota.getMonthlyLimit()) {
                        return Mono.error(new RateLimitExceededException(
                                "每月", quota.getMonthlyLimit(), quota.getMonthlyUsed()));
                    }
                    
                    log.debug("速率限制检查通过: userId={}", userId);
                    return Mono.just(quota);
                })
                .doOnError(error -> {
                    if (error instanceof RateLimitExceededException) {
                        log.warn("用户超过速率限制: userId={}, error={}", userId, error.getMessage());
                    }
                });
    }

    @Override
    public Mono<AiUsageQuota> incrementUsage(Long userId) {
        log.debug("增加用户使用次数: userId={}", userId);
        
        return getQuota(userId)
                .flatMap(quota -> {
                    // 增加使用次数
                    quota.setHourlyUsed(quota.getHourlyUsed() + 1);
                    quota.setDailyUsed(quota.getDailyUsed() + 1);
                    quota.setMonthlyUsed(quota.getMonthlyUsed() + 1);
                    quota.setUpdateTime(LocalDateTime.now());
                    
                    return quotaRepository.save(quota);
                })
                .flatMap(quota -> {
                    // 同步到Redis缓存
                    return syncToRedis(quota).thenReturn(quota);
                })
                .doOnSuccess(quota -> log.debug("使用次数增加成功: userId={}, hourly={}/{}, daily={}/{}, monthly={}/{}",
                        userId, quota.getHourlyUsed(), quota.getHourlyLimit(),
                        quota.getDailyUsed(), quota.getDailyLimit(),
                        quota.getMonthlyUsed(), quota.getMonthlyLimit()))
                .doOnError(error -> log.error("增加使用次数失败: userId={}", userId, error));
    }

    @Override
    public Mono<AiUsageQuota> checkAndIncrement(Long userId) {
        log.debug("检查并增加使用次数: userId={}", userId);
        
        return checkLimit(userId)
                .flatMap(quota -> incrementUsage(userId));
    }

    @Override
    public Mono<AiUsageQuota> getQuota(Long userId) {
        log.debug("获取用户配额: userId={}", userId);
        
        return quotaRepository.findByUserId(userId)
                .switchIfEmpty(createDefaultQuota(userId))
                .flatMap(this::checkAndResetExpiredQuota);
    }

    @Override
    public Mono<AiUsageQuota> setQuota(Long userId, Integer hourlyLimit, Integer dailyLimit, Integer monthlyLimit) {
        log.debug("设置用户配额: userId={}, hourly={}, daily={}, monthly={}",
                userId, hourlyLimit, dailyLimit, monthlyLimit);
        
        return quotaRepository.findByUserId(userId)
                .switchIfEmpty(createDefaultQuota(userId))
                .flatMap(quota -> {
                    quota.setHourlyLimit(hourlyLimit);
                    quota.setDailyLimit(dailyLimit);
                    quota.setMonthlyLimit(monthlyLimit);
                    quota.setUpdateTime(LocalDateTime.now());
                    
                    return quotaRepository.save(quota);
                })
                .flatMap(quota -> syncToRedis(quota).thenReturn(quota))
                .doOnSuccess(quota -> log.info("配额设置成功: userId={}", userId))
                .doOnError(error -> log.error("配额设置失败: userId={}", userId, error));
    }

    @Override
    public Mono<AiUsageQuota> resetQuota(Long userId) {
        log.debug("重置用户配额: userId={}", userId);
        
        return quotaRepository.findByUserId(userId)
                .flatMap(quota -> {
                    LocalDateTime now = LocalDateTime.now();
                    quota.setHourlyUsed(0);
                    quota.setDailyUsed(0);
                    quota.setMonthlyUsed(0);
                    quota.setLastResetHour(now);
                    quota.setLastResetDay(now);
                    quota.setLastResetMonth(now);
                    quota.setUpdateTime(now);
                    
                    return quotaRepository.save(quota);
                })
                .flatMap(quota -> syncToRedis(quota).thenReturn(quota))
                .doOnSuccess(quota -> log.info("配额重置成功: userId={}", userId))
                .doOnError(error -> log.error("配额重置失败: userId={}", userId, error));
    }

    @Override
    public Mono<Boolean> checkAndResetExpired(AiUsageQuota quota) {
        LocalDateTime now = LocalDateTime.now();
        boolean needsUpdate = false;
        
        // 检查小时配额是否需要重置
        if (quota.getLastResetHour() == null || 
            ChronoUnit.HOURS.between(quota.getLastResetHour(), now) >= 1) {
            quota.setHourlyUsed(0);
            quota.setLastResetHour(now);
            needsUpdate = true;
            log.debug("重置小时配额: userId={}", quota.getUserId());
        }
        
        // 检查每日配额是否需要重置
        if (quota.getLastResetDay() == null || 
            !quota.getLastResetDay().toLocalDate().equals(now.toLocalDate())) {
            quota.setDailyUsed(0);
            quota.setLastResetDay(now);
            needsUpdate = true;
            log.debug("重置每日配额: userId={}", quota.getUserId());
        }
        
        // 检查每月配额是否需要重置
        if (quota.getLastResetMonth() == null || 
            quota.getLastResetMonth().getMonth() != now.getMonth() ||
            quota.getLastResetMonth().getYear() != now.getYear()) {
            quota.setMonthlyUsed(0);
            quota.setLastResetMonth(now);
            needsUpdate = true;
            log.debug("重置每月配额: userId={}", quota.getUserId());
        }
        
        if (needsUpdate) {
            quota.setUpdateTime(now);
            return quotaRepository.save(quota)
                    .flatMap(updated -> syncToRedis(updated).thenReturn(true));
        }
        
        return Mono.just(false);
    }

    /**
     * 检查并重置过期的配额（返回配额对象）
     */
    private Mono<AiUsageQuota> checkAndResetExpiredQuota(AiUsageQuota quota) {
        LocalDateTime now = LocalDateTime.now();
        boolean needsUpdate = false;
        
        // 检查小时配额是否需要重置
        if (quota.getLastResetHour() == null || 
            ChronoUnit.HOURS.between(quota.getLastResetHour(), now) >= 1) {
            quota.setHourlyUsed(0);
            quota.setLastResetHour(now);
            needsUpdate = true;
            log.debug("重置小时配额: userId={}", quota.getUserId());
        }
        
        // 检查每日配额是否需要重置
        if (quota.getLastResetDay() == null || 
            !quota.getLastResetDay().toLocalDate().equals(now.toLocalDate())) {
            quota.setDailyUsed(0);
            quota.setLastResetDay(now);
            needsUpdate = true;
            log.debug("重置每日配额: userId={}", quota.getUserId());
        }
        
        // 检查每月配额是否需要重置
        if (quota.getLastResetMonth() == null || 
            quota.getLastResetMonth().getMonth() != now.getMonth() ||
            quota.getLastResetMonth().getYear() != now.getYear()) {
            quota.setMonthlyUsed(0);
            quota.setLastResetMonth(now);
            needsUpdate = true;
            log.debug("重置每月配额: userId={}", quota.getUserId());
        }
        
        if (needsUpdate) {
            quota.setUpdateTime(now);
            return quotaRepository.save(quota)
                    .flatMap(updated -> syncToRedis(updated).thenReturn(updated));
        }
        
        return Mono.just(quota);
    }

    @Override
    public Mono<QuotaRemaining> getRemainingQuota(Long userId) {
        log.debug("获取剩余配额: userId={}", userId);
        
        return getQuota(userId)
                .map(quota -> {
                    int hourlyRemaining = Math.max(0, quota.getHourlyLimit() - quota.getHourlyUsed());
                    int dailyRemaining = Math.max(0, quota.getDailyLimit() - quota.getDailyUsed());
                    int monthlyRemaining = Math.max(0, quota.getMonthlyLimit() - quota.getMonthlyUsed());
                    
                    return new QuotaRemaining(hourlyRemaining, dailyRemaining, monthlyRemaining);
                });
    }

    /**
     * 获取指定时间段的剩余配额
     */
    public Mono<Long> getRemainingQuota(Long userId, String period) {
        log.debug("获取剩余配额: userId={}, period={}", userId, period);
        
        return getQuota(userId)
                .map(quota -> {
                    switch (period.toLowerCase()) {
                        case "hour":
                            return (long) Math.max(0, quota.getHourlyLimit() - quota.getHourlyUsed());
                        case "day":
                            return (long) Math.max(0, quota.getDailyLimit() - quota.getDailyUsed());
                        case "month":
                            return (long) Math.max(0, quota.getMonthlyLimit() - quota.getMonthlyUsed());
                        default:
                            log.warn("未知的时间段类型: {}", period);
                            return 0L;
                    }
                });
    }

    /**
     * 创建默认配额
     */
    private Mono<AiUsageQuota> createDefaultQuota(Long userId) {
        log.debug("创建默认配额: userId={}", userId);
        
        LocalDateTime now = LocalDateTime.now();
        AiUsageQuota quota = AiUsageQuota.builder()
                .userId(userId)
                .roleId(null)  // 明确设置为null
                .hourlyLimit(DEFAULT_HOURLY_LIMIT)
                .dailyLimit(DEFAULT_DAILY_LIMIT)
                .monthlyLimit(DEFAULT_MONTHLY_LIMIT)
                .hourlyUsed(0)
                .dailyUsed(0)
                .monthlyUsed(0)
                .lastResetHour(now)
                .lastResetDay(now)
                .lastResetMonth(now)
                .createTime(now)
                .updateTime(now)
                .isDeleted(false)  // 明确设置删除标记
                .build();
        
        return quotaRepository.save(quota)
                .flatMap(saved -> syncToRedis(saved).thenReturn(saved))
                .doOnSuccess(saved -> log.info("默认配额创建成功: userId={}", userId));
    }

    /**
     * 同步配额到Redis缓存
     */
    private Mono<Boolean> syncToRedis(AiUsageQuota quota) {
        String key = REDIS_KEY_PREFIX + quota.getUserId();
        
        // 计算到下一个小时的过期时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextHour = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
        Duration ttl = Duration.between(now, nextHour);
        
        return redisTemplate.opsForValue()
                .set(key, String.valueOf(quota.getHourlyUsed()), ttl)
                .doOnSuccess(result -> log.debug("配额同步到Redis: userId={}, ttl={}s", 
                        quota.getUserId(), ttl.getSeconds()))
                .onErrorResume(error -> {
                    log.warn("配额同步到Redis失败: userId={}", quota.getUserId(), error);
                    return Mono.just(false);
                });
    }
}
