package com.ryu.blog.service.impl;

import com.ryu.blog.entity.AiUsageQuota;
import com.ryu.blog.exception.RateLimitExceededException;
import com.ryu.blog.repository.AiUsageQuotaRepository;
import com.ryu.blog.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 速率限制服务实现
 *
 * <p><b>配额判定改成 Redis Lua 脚本原子执行</b>。
 *
 * <p>原先的流程是「读 DB 配额 → 判断 → 再读一次 DB → 加一 → 写回 DB」，
 * 有两个问题：
 * <ol>
 *   <li><b>TOCTOU</b>：判断和自增之间不是原子的，两个并发请求会同时通过判断、
 *       基于同一个基数各自 +1，后写的覆盖先写的 → <b>计数丢失，配额可超发</b>；</li>
 *   <li><b>每次 AI 调用都要读写 DB 一次</b>，而 AI 调用本身要花几秒，这点开销不大，
 *       但配额判定没必要依赖 DB 的行锁。</li>
 * </ol>
 *
 * <p>现在：Redis 里用三个带窗口 TTL 的计数器（时/日/月）记录用量，
 * 由一段 Lua 脚本完成「读计数 → 比限额 → 不过就 +1 并设 TTL」的完整判定。
 * Redis 执行脚本是原子的，所以并发下不会超发。
 *
 * <p><b>fail-closed</b>：脚本执行失败（Redis 不可用等）时整个调用链报错，
 * AI 请求被拒绝 —— 而不是「判��不了就放行」。
 *
 * <p>DB 里的 {@code t_ai_usage_quota} 只负责保存<b>限额</b>；
 * 用量以 Redis 计数器为准（读配额时会把 Redis 的计数合并回返回值，
 * 所以管理端看到的数字仍然是准的）。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private final AiUsageQuotaRepository quotaRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // 默认配额
    private static final int DEFAULT_HOURLY_LIMIT = 10;
    private static final int DEFAULT_DAILY_LIMIT = 50;
    private static final int DEFAULT_MONTHLY_LIMIT = 1000;

    // Redis键前缀
    private static final String REDIS_KEY_PREFIX = "ai:quota:";
    /** 用量计数器前缀：ai:quota:used:{userId}:{hour|day|month} */
    private static final String REDIS_USED_KEY_PREFIX = REDIS_KEY_PREFIX + "used:";
    /** 限额缓存前缀：ai:quota:limits:{userId} */
    private static final String REDIS_LIMITS_KEY_PREFIX = REDIS_KEY_PREFIX + "limits:";

    /** 限额在 Redis 里的缓存时长（秒）。限额很少变，缓存一下省掉每次 AI 调用都读库 */
    private static final long LIMITS_CACHE_TTL_SECONDS = 60;

    /**
     * 配额原子判定脚本
     *
     * <p>KEYS[1..3] = 时/日/月 三个计数器
     * <p>ARGV[1..3] = 时/日/月 限额
     * <p>ARGV[4..6] = 时/日/月 计数器的 TTL（秒）
     * <p>返回 {是否放行, 时用量, 日用量, 月用量}
     *
     * <p>先判断再自增：超限时不加计数，避免把计数灌大。
     */
    private static final String QUOTA_SCRIPT = """
            local h = tonumber(redis.call('GET', KEYS[1]) or '0')
            local d = tonumber(redis.call('GET', KEYS[2]) or '0')
            local m = tonumber(redis.call('GET', KEYS[3]) or '0')
            local lh = tonumber(ARGV[1])
            local ld = tonumber(ARGV[2])
            local lm = tonumber(ARGV[3])
            if (lh > 0 and h >= lh) or (ld > 0 and d >= ld) or (lm > 0 and m >= lm) then
              return {0, h, d, m}
            end
            h = redis.call('INCR', KEYS[1])
            if h == 1 then redis.call('EXPIRE', KEYS[1], ARGV[4]) end
            d = redis.call('INCR', KEYS[2])
            if d == 1 then redis.call('EXPIRE', KEYS[2], ARGV[5]) end
            m = redis.call('INCR', KEYS[3])
            if m == 1 then redis.call('EXPIRE', KEYS[3], ARGV[6]) end
            return {1, h, d, m}
            """;

    @SuppressWarnings("unchecked")
    private static final RedisScript<List<Long>> QUOTA_SCRIPT_INSTANCE =
            (RedisScript<List<Long>>) (RedisScript<?>) RedisScript.of(QUOTA_SCRIPT, List.class);

    @Override
    public Mono<AiUsageQuota> checkLimit(Long userId) {
        log.debug("检查用户速率限制: userId={}", userId);

        return getLimits(userId)
                .flatMap(limits -> readCounters(userId)
                        .map(used -> {
                            QuotaResult result = new QuotaResult(true,
                                    limits.hourlyLimit(), limits.dailyLimit(), limits.monthlyLimit(),
                                    used.hour(), used.day(), used.month());
                            RateLimitExceededException exceeded = findExceeded(result);
                            if (exceeded != null) {
                                throw exceeded;
                            }
                            return buildQuota(userId, limits, result);
                        }))
                .doOnError(error -> {
                    if (error instanceof RateLimitExceededException) {
                        log.warn("用户超过速率限制: userId={}, error={}", userId, error.getMessage());
                    }
                });
    }

    @Override
    public Mono<AiUsageQuota> incrementUsage(Long userId) {
        log.debug("增加用户使用次数: userId={}", userId);

        // 自增计数（不抛超限异常；脚本本身在已超限时不会再加计数）
        return getLimits(userId)
                .flatMap(limits -> executeQuotaScript(userId, limits, false));
    }

    @Override
    public Mono<AiUsageQuota> checkAndIncrement(Long userId) {
        log.debug("检查并增加用户使用次数: userId={}", userId);

        return getLimits(userId)
                .flatMap(limits -> executeQuotaScript(userId, limits, true))
                .doOnError(error -> {
                    if (error instanceof RateLimitExceededException) {
                        log.warn("用户超过速率限制: userId={}, error={}", userId, error.getMessage());
                    }
                });
    }

    @Override
    public Mono<AiUsageQuota> getQuota(Long userId) {
        log.debug("获取用户配额: userId={}", userId);

        // 返回值里的「已用量」以 Redis 计数器为准（DB 里的 used 字段不再实时维护）
        return getLimits(userId)
                .flatMap(limits -> readCounters(userId)
                        .map(used -> buildQuota(userId, limits,
                                new QuotaResult(true, limits.hourlyLimit(), limits.dailyLimit(), limits.monthlyLimit(),
                                        used.hour(), used.day(), used.month()))));
    }

    @Override
    public Mono<AiUsageQuota> setQuota(Long userId, Integer hourlyLimit, Integer dailyLimit, Integer monthlyLimit) {
        log.debug("设置用户配额: userId={}, hourly={}, daily={}, monthly={}",
                userId, hourlyLimit, dailyLimit, monthlyLimit);

        return quotaRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() -> createDefaultQuota(userId)))
                .flatMap(quota -> {
                    if (hourlyLimit != null) {
                        quota.setHourlyLimit(hourlyLimit);
                    }
                    if (dailyLimit != null) {
                        quota.setDailyLimit(dailyLimit);
                    }
                    if (monthlyLimit != null) {
                        quota.setMonthlyLimit(monthlyLimit);
                    }
                    quota.setUpdateTime(LocalDateTime.now());
                    return quotaRepository.save(quota);
                })
                .flatMap(saved -> {
                    // 限额变了，缓存要失效
                    evictLimitsCache(userId);
                    return getQuota(userId);
                })
                .doOnSuccess(quota -> log.info("用户配额设置成功: userId={}, hourly={}/{}, daily={}/{}, monthly={}/{}",
                        userId, quota.getHourlyUsed(), quota.getHourlyLimit(),
                        quota.getDailyUsed(), quota.getDailyLimit(),
                        quota.getMonthlyUsed(), quota.getMonthlyLimit()));
    }

    @Override
    public Mono<AiUsageQuota> resetQuota(Long userId) {
        log.info("重置用户配额: userId={}", userId);

        return resetCounters(userId)
                .then(quotaRepository.findByUserId(userId)
                        .flatMap(quota -> {
                            quota.setHourlyUsed(0);
                            quota.setDailyUsed(0);
                            quota.setMonthlyUsed(0);
                            quota.setLastResetHour(LocalDateTime.now());
                            quota.setLastResetDay(LocalDateTime.now());
                            quota.setLastResetMonth(LocalDateTime.now());
                            quota.setUpdateTime(LocalDateTime.now());
                            return quotaRepository.save(quota);
                        }))
                .then(getQuota(userId));
    }

    @Override
    public Mono<Boolean> checkAndResetExpired(AiUsageQuota quota) {
        if (quota == null) {
            return Mono.just(false);
        }
        // 窗口重置交给 Redis 计数器的 TTL 负责，这里只需清掉过期的计数
        return resetCounters(quota.getUserId()).thenReturn(true);
    }

    @Override
    public Mono<QuotaRemaining> getRemainingQuota(Long userId) {
        log.debug("获取剩余配额: userId={}", userId);

        return getLimits(userId)
                .flatMap(limits -> readCounters(userId)
                        .map(used -> new QuotaRemaining(
                                Math.max(0, limits.hourlyLimit() - used.hour()),
                                Math.max(0, limits.dailyLimit() - used.day()),
                                Math.max(0, limits.monthlyLimit() - used.month()))));
    }

    /**
     * 获取指定时间段的剩余配额
     */
    public Mono<Long> getRemainingQuota(Long userId, String period) {
        log.debug("获取剩余配额: userId={}, period={}", userId, period);

        return getLimits(userId)
                .flatMap(limits -> readCounters(userId)
                        .map(used -> {
                            if (period == null) {
                                return 0L;
                            }
                            return switch (period.toLowerCase()) {
                                case "hour" -> (long) Math.max(0, limits.hourlyLimit() - used.hour());
                                case "day" -> (long) Math.max(0, limits.dailyLimit() - used.day());
                                case "month" -> (long) Math.max(0, limits.monthlyLimit() - used.month());
                                default -> {
                                    log.warn("未知的时间段类型: {}", period);
                                    yield 0L;
                                }
                            };
                        }));
    }

    // ==================================================================
    // 内部实现
    // ==================================================================

    /**
     * 执行配额判定脚本
     *
     * @param userId 用户ID
     * @param limits 限额
     * @param enforceLimit true = 判断限额（超了就报错）；false = 只自增
     * @return 配额
     */
    private Mono<AiUsageQuota> executeQuotaScript(Long userId, QuotaLimits limits, boolean enforceLimit) {
        List<String> keys = List.of(
                REDIS_USED_KEY_PREFIX + userId + ":hour",
                REDIS_USED_KEY_PREFIX + userId + ":day",
                REDIS_USED_KEY_PREFIX + userId + ":month");

        // 脚本参数必须传 String：模板的 value 序列化器是 StringRedisSerializer，
        // 传 Integer 会在运行期抛 ClassCastException
        List<String> args = List.of(
                String.valueOf(limits.hourlyLimit()),
                String.valueOf(limits.dailyLimit()),
                String.valueOf(limits.monthlyLimit()),
                String.valueOf(secondsUntilNextHour()),
                String.valueOf(secondsUntilNextDay()),
                String.valueOf(secondsUntilNextMonth()));

        return redisTemplate.execute(QUOTA_SCRIPT_INSTANCE, keys, args)
                // execute 返回的是 Flux（脚本理论上可返回多行），这里脚本只返回一行
                .next()
                .map(raw -> toQuotaResult(raw, limits))
                .defaultIfEmpty(new QuotaResult(false,
                        limits.hourlyLimit(), limits.dailyLimit(), limits.monthlyLimit(), 0, 0, 0))
                .flatMap(result -> {
                    if (enforceLimit && !result.allowed()) {
                        RateLimitExceededException exceeded = findExceeded(result);
                        return Mono.error(exceeded != null
                                ? exceeded
                                : new RateLimitExceededException("每小时",
                                        result.hourlyLimit(), result.hourlyUsed()));
                    }
                    return Mono.just(buildQuota(userId, limits, result));
                });
    }

    /**
     * 把脚本返回值转成判定结果
     *
     * @param raw    脚本返回值 {allowed, hour, day, month}
     * @param limits 限额（脚本不返回限额，从外面补进来）
     * @return 判定结果
     */
    private QuotaResult toQuotaResult(List<?> raw, QuotaLimits limits) {
        if (raw == null || raw.size() < 4) {
            log.warn("配额脚本返回值异常: {}", raw);
            return new QuotaResult(false,
                    limits.hourlyLimit(), limits.dailyLimit(), limits.monthlyLimit(), 0, 0, 0);
        }
        return new QuotaResult(
                toInt(raw.get(0)) == 1,
                limits.hourlyLimit(), limits.dailyLimit(), limits.monthlyLimit(),
                toInt(raw.get(1)), toInt(raw.get(2)), toInt(raw.get(3)));
    }

    /**
     * 找出超限的那一档
     *
     * @param result 判定结果
     * @return 超限异常；没超限返回 null
     */
    private RateLimitExceededException findExceeded(QuotaResult result) {
        if (result.hourlyLimit() > 0 && result.hourlyUsed() >= result.hourlyLimit()) {
            return new RateLimitExceededException("每小时", result.hourlyLimit(), result.hourlyUsed());
        }
        if (result.dailyLimit() > 0 && result.dailyUsed() >= result.dailyLimit()) {
            return new RateLimitExceededException("每日", result.dailyLimit(), result.dailyUsed());
        }
        if (result.monthlyLimit() > 0 && result.monthlyUsed() >= result.monthlyLimit()) {
            return new RateLimitExceededException("每月", result.monthlyLimit(), result.monthlyUsed());
        }
        return null;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 构造超限异常（指出是哪一档超了）
     */
    /**
     * 读取当前用量（不修改）
     */
    private Mono<Counters> readCounters(Long userId) {
        return redisTemplate.opsForValue()
                .multiGet(List.of(
                        REDIS_USED_KEY_PREFIX + userId + ":hour",
                        REDIS_USED_KEY_PREFIX + userId + ":day",
                        REDIS_USED_KEY_PREFIX + userId + ":month"))
                .map(values -> new Counters(
                        values != null && values.size() > 0 ? toInt(values.get(0)) : 0,
                        values != null && values.size() > 1 ? toInt(values.get(1)) : 0,
                        values != null && values.size() > 2 ? toInt(values.get(2)) : 0))
                .defaultIfEmpty(new Counters(0, 0, 0));
    }

    /**
     * 清空用量计数器
     */
    private Mono<Void> resetCounters(Long userId) {
        // ReactiveRedisTemplate 只有 delete(String...) 与 delete(Publisher<String>)，
        // 没有 delete(List<String>) 重载
        String[] keys = {
                REDIS_USED_KEY_PREFIX + userId + ":hour",
                REDIS_USED_KEY_PREFIX + userId + ":day",
                REDIS_USED_KEY_PREFIX + userId + ":month"
        };
        return redisTemplate.delete(keys)
                .doOnSuccess(ignored -> log.debug("配额计数器已重置: userId={}", userId))
                .then();
    }

    /**
     * 取限额：先看 Redis 缓存，没有再读库（读库后回填缓存）
     */
    private Mono<QuotaLimits> getLimits(Long userId) {
        String cacheKey = REDIS_LIMITS_KEY_PREFIX + userId;

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> parseLimits(cached))
                .switchIfEmpty(Mono.defer(() ->
                        loadLimitsFromDb(userId)
                                .flatMap(limits -> redisTemplate.opsForValue()
                                        .set(cacheKey, serializeLimits(limits),
                                                Duration.ofSeconds(LIMITS_CACHE_TTL_SECONDS))
                                        .thenReturn(limits)
                                        .onErrorResume(e -> Mono.just(limits)))))
                // Redis 读失败就退回 DB（脚本那步失败会 fail-closed）
                .onErrorResume(e -> loadLimitsFromDb(userId));
    }

    /**
     * 从 DB 读限额（没有记录就建一条默认的）
     */
    private Mono<QuotaLimits> loadLimitsFromDb(Long userId) {
        return quotaRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() -> createDefaultQuota(userId)))
                .map(quota -> new QuotaLimits(
                        quota.getHourlyLimit() != null ? quota.getHourlyLimit() : DEFAULT_HOURLY_LIMIT,
                        quota.getDailyLimit() != null ? quota.getDailyLimit() : DEFAULT_DAILY_LIMIT,
                        quota.getMonthlyLimit() != null ? quota.getMonthlyLimit() : DEFAULT_MONTHLY_LIMIT));
    }

    /**
     * 失效限额缓存
     */
    private Mono<Void> evictLimitsCache(Long userId) {
        return redisTemplate.delete(REDIS_LIMITS_KEY_PREFIX + userId).then();
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
                .doOnSuccess(saved -> log.info("默认配额创建成功: userId={}", userId));
    }

    /**
     * 组装返回给调用方的配额对象
     */
    private AiUsageQuota buildQuota(Long userId, QuotaLimits limits, QuotaResult result) {
        LocalDateTime now = LocalDateTime.now();
        return AiUsageQuota.builder()
                .userId(userId)
                .hourlyLimit(limits.hourlyLimit())
                .dailyLimit(limits.dailyLimit())
                .monthlyLimit(limits.monthlyLimit())
                .hourlyUsed(result.hourlyUsed())
                .dailyUsed(result.dailyUsed())
                .monthlyUsed(result.monthlyUsed())
                .lastResetHour(now)
                .lastResetDay(now)
                .lastResetMonth(now)
                .createTime(now)
                .updateTime(now)
                .isDeleted(false)
                .build();
    }

    private String serializeLimits(QuotaLimits limits) {
        return limits.hourlyLimit() + ":" + limits.dailyLimit() + ":" + limits.monthlyLimit();
    }

    private Mono<QuotaLimits> parseLimits(String cached) {
        try {
            String[] parts = cached.split(":");
            if (parts.length != 3) {
                return Mono.empty();
            }
            return Mono.just(new QuotaLimits(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())));
        } catch (RuntimeException e) {
            log.warn("限额缓存解析失败，按未命中处理: {}", cached);
            return Mono.empty();
        }
    }

    private long secondsUntilNextHour() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
        return Math.max(1, Duration.between(now, next).getSeconds());
    }

    private long secondsUntilNextDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.plusDays(1).truncatedTo(ChronoUnit.DAYS);
        return Math.max(1, Duration.between(now, next).getSeconds());
    }

    private long secondsUntilNextMonth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.plusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
        return Math.max(1, Duration.between(now, next).getSeconds());
    }

    /**
     * 限额
     */
    private record QuotaLimits(int hourlyLimit, int dailyLimit, int monthlyLimit) {
    }

    /**
     * 用量
     */
    private record Counters(int hour, int day, int month) {
    }

    /**
     * 配额判定结果
     */
    private record QuotaResult(boolean allowed,
                               int hourlyLimit, int dailyLimit, int monthlyLimit,
                               int hourlyUsed, int dailyUsed, int monthlyUsed) {
    }
}
