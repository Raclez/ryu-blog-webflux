package com.ryu.blog.service;

import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 任务配额服务
 * 管理用户的任务配额限制，使用 Redis 实现分布式配额控制
 * 
 * <p>特性：
 * <ul>
 *   <li>基于小时的滑动窗口配额限制</li>
 *   <li>使用 Lua 脚本保证原子性操作</li>
 *   <li>支持配置化的配额限制</li>
 *   <li>提供配额查询和重置功能</li>
 * </ul>
 * 
 * @author ryu
 */
@Slf4j
@Service
public class TaskQuotaService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    private static final String QUOTA_KEY_PREFIX = "task:quota:";
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    
    @Value("${task.executor.quota.high-priority-per-hour:5}")
    private int highPriorityQuotaPerHour;
    
    @Value("${task.executor.quota.ttl-hours:1}")
    private int quotaTtlHours;
    
    /**
     * Lua 脚本：原子性地检查并增加配额
     * 返回值：当前计数（增加后的值）
     * 如果超过限制，返回 -1
     */
    private static final String CHECK_AND_INCREMENT_SCRIPT = 
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local ttl = tonumber(ARGV[2])\n" +
            "local current = redis.call('GET', key)\n" +
            "if current and tonumber(current) >= limit then\n" +
            "    return -1\n" +
            "end\n" +
            "local count = redis.call('INCR', key)\n" +
            "if count == 1 then\n" +
            "    redis.call('EXPIRE', key, ttl)\n" +
            "end\n" +
            "return count";
    
    public TaskQuotaService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 检查并消费配额
     * 使用 Lua 脚本保证原子性操作，避免竞态条件
     * 
     * @param userId 用户ID
     * @param priority 任务优先级
     * @return Mono<Void> 如果配额充足则成功，否则抛出 RateLimitExceededException
     */
    public Mono<Void> checkAndConsumeQuota(Long userId, TaskPriority priority) {
        // 只对高优先级任务进行配额限制
        if (priority != TaskPriority.HIGH) {
            return Mono.empty();
        }
        
        String quotaKey = buildQuotaKey(userId, priority);
        int ttlSeconds = quotaTtlHours * 3600;
        
        RedisScript<Long> script = RedisScript.of(CHECK_AND_INCREMENT_SCRIPT, Long.class);
        
        return redisTemplate.execute(
                script,
                Collections.singletonList(quotaKey),
                java.util.Arrays.asList(
                        String.valueOf(highPriorityQuotaPerHour),
                        String.valueOf(ttlSeconds)
                )
        )
        .next()
        .flatMap(count -> {
            if (count == -1) {
                // 配额已用完
                log.warn("User {} exceeded high priority quota: limit={}/hour", 
                        userId, highPriorityQuotaPerHour);
                return Mono.error(new RateLimitExceededException(
                        "高优先级任务",
                        highPriorityQuotaPerHour,
                        highPriorityQuotaPerHour
                ));
            }
            
            log.debug("User {} consumed high priority quota: {}/{}", 
                    userId, count, highPriorityQuotaPerHour);
            return Mono.<Void>empty();
        })
        .onErrorResume(RateLimitExceededException.class, Mono::error)
        .onErrorResume(error -> {
            log.error("Failed to check quota for user {}: {}", userId, error.getMessage(), error);
            // Redis 错误时，为了系统可用性，允许请求通过（降级策略）
            log.warn("Quota check failed, allowing request to proceed (degraded mode)");
            return Mono.empty();
        });
    }
    
    /**
     * 获取用户当前配额使用情况
     * 
     * @param userId 用户ID
     * @param priority 任务优先级
     * @return Mono<Long> 当前已使用的配额数
     */
    public Mono<Long> getQuotaUsage(Long userId, TaskPriority priority) {
        if (priority != TaskPriority.HIGH) {
            return Mono.just(0L);
        }
        
        String quotaKey = buildQuotaKey(userId, priority);
        
        return redisTemplate.opsForValue()
                .get(quotaKey)
                .map(Long::parseLong)
                .defaultIfEmpty(0L)
                .doOnSuccess(count -> 
                    log.debug("User {} high priority quota usage: {}/{}", 
                            userId, count, highPriorityQuotaPerHour)
                )
                .onErrorResume(error -> {
                    log.error("Failed to get quota usage for user {}: {}", userId, error.getMessage());
                    return Mono.just(0L);
                });
    }
    
    /**
     * 获取用户剩余配额
     * 
     * @param userId 用户ID
     * @param priority 任务优先级
     * @return Mono<Long> 剩余配额数
     */
    public Mono<Long> getRemainingQuota(Long userId, TaskPriority priority) {
        return getQuotaUsage(userId, priority)
                .map(used -> Math.max(0, highPriorityQuotaPerHour - used));
    }
    
    /**
     * 获取配额限制值
     * 
     * @param priority 任务优先级
     * @return 配额限制值
     */
    public int getQuotaLimit(TaskPriority priority) {
        if (priority == TaskPriority.HIGH) {
            return highPriorityQuotaPerHour;
        }
        return Integer.MAX_VALUE; // 其他优先级无限制
    }
    
    /**
     * 重置用户配额（管理员功能）
     * 
     * @param userId 用户ID
     * @param priority 任务优先级
     * @return Mono<Boolean> 是否成功重置
     */
    public Mono<Boolean> resetQuota(Long userId, TaskPriority priority) {
        if (priority != TaskPriority.HIGH) {
            return Mono.just(false);
        }
        
        String quotaKey = buildQuotaKey(userId, priority);
        
        return redisTemplate.delete(quotaKey)
                .map(count -> count > 0)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Reset quota for user {}, priority {}", userId, priority);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to reset quota for user {}: {}", userId, error.getMessage());
                    return Mono.just(false);
                });
    }
    
    /**
     * 构建配额 Redis Key
     * 格式：task:quota:{userId}:HIGH:{hour}
     * 
     * @param userId 用户ID
     * @param priority 任务优先级
     * @return Redis Key
     */
    private String buildQuotaKey(Long userId, TaskPriority priority) {
        String hour = LocalDateTime.now().format(HOUR_FORMATTER);
        return String.format("%s%d:%s:%s", QUOTA_KEY_PREFIX, userId, priority.name(), hour);
    }
}
