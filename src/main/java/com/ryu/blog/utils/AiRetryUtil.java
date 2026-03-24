package com.ryu.blog.utils;

import com.ryu.blog.config.AiRetryConfig;
import com.ryu.blog.exception.AiProviderException;
import com.ryu.blog.exception.AiTimeoutException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * AI重试工具类
 * 
 * <p>提供指数退避重试机制。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
//@Component
public class AiRetryUtil {

    private final AiRetryConfig retryConfig;

    public AiRetryUtil(AiRetryConfig retryConfig) {
        this.retryConfig = retryConfig;
    }

    /**
     * 创建重试规范
     * 
     * @return 重试规范
     */
    public Retry createRetrySpec() {
        return Retry.backoff(retryConfig.getMaxAttempts(), Duration.ofMillis(retryConfig.getInitialBackoff()))
                .maxBackoff(Duration.ofMillis(retryConfig.getMaxBackoff()))
                .filter(this::isRetryableException)
                .doBeforeRetry(retrySignal -> {
                    log.warn("AI请求失败，正在重试: attempt={}/{}, error={}", 
                            retrySignal.totalRetries() + 1, 
                            retryConfig.getMaxAttempts(),
                            retrySignal.failure().getMessage());
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("AI请求重试次数已用尽: attempts={}", retrySignal.totalRetries());
                    return retrySignal.failure();
                });
    }

    /**
     * 创建带超时的重试规范
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @return 重试规范
     */
    public Retry createRetrySpecWithTimeout(long timeoutMillis) {
        return Retry.backoff(retryConfig.getMaxAttempts(), Duration.ofMillis(retryConfig.getInitialBackoff()))
                .maxBackoff(Duration.ofMillis(retryConfig.getMaxBackoff()))
                .filter(this::isRetryableException)
                .doBeforeRetry(retrySignal -> {
                    log.warn("AI请求失败，正在重试: attempt={}/{}, error={}", 
                            retrySignal.totalRetries() + 1, 
                            retryConfig.getMaxAttempts(),
                            retrySignal.failure().getMessage());
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("AI请求重试次数已用尽: attempts={}", retrySignal.totalRetries());
                    return new AiTimeoutException("请求超时，已重试" + retrySignal.totalRetries() + "次", timeoutMillis);
                });
    }

    /**
     * 应用重试到Mono
     * 
     * @param mono 原始Mono
     * @param <T> 返回类型
     * @return 带重试的Mono
     */
    public <T> Mono<T> withRetry(Mono<T> mono) {
        if (!retryConfig.isEnabled()) {
            return mono;
        }
        
        return mono
                .timeout(Duration.ofMillis(retryConfig.getTimeout()))
                .retryWhen(createRetrySpec());
    }

    /**
     * 应用重试到Mono（自定义超时）
     * 
     * @param mono 原始Mono
     * @param timeoutMillis 超时时间（毫秒）
     * @param <T> 返回类型
     * @return 带重试的Mono
     */
    public <T> Mono<T> withRetry(Mono<T> mono, long timeoutMillis) {
        if (!retryConfig.isEnabled()) {
            return mono.timeout(Duration.ofMillis(timeoutMillis));
        }
        
        return mono
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(createRetrySpecWithTimeout(timeoutMillis));
    }

    /**
     * 判断异常是否可重试
     * 
     * @param throwable 异常
     * @return 是否可重试
     */
    private boolean isRetryableException(Throwable throwable) {
        // 超时异常可重试
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        
        // 网络异常可重试
        if (throwable instanceof java.io.IOException) {
            return true;
        }
        
        // AI提供商限流可重试
        if (throwable instanceof AiProviderException) {
            AiProviderException ex = (AiProviderException) throwable;
            String message = ex.getMessage().toLowerCase();
            return message.contains("rate limit") || 
                   message.contains("限流") || 
                   message.contains("too many requests");
        }
        
        // 其他异常不重试
        return false;
    }
}
