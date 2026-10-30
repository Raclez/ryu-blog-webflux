package com.ryu.blog.service.ai;

import com.ryu.blog.config.ConditionalOnAiEnabled;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.utils.AiRetryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * AI 调用闸门
 *
 * <p>解决两个问题：
 *
 * <ol>
 *   <li><b>并发闸门（bulkhead）</b>：Spring AI 1.0.0-M5 的 ChatClient 底层是阻塞的
 *       RestClient，一次 AI 调用会<b>占住一个 boundedElastic 线程直到响应结束</b>。
 *       没有上限时，并发请求会迅速打满弹性线程池，连带把正常的 Web 请求饿死。
 *       这里用信号量限制同时在飞的 AI 请求数，超出直接快速失败（而不是排队堆积）。</li>
 *   <li><b>瞬时故障重试</b>：429 限流、5xx 网关错误、超时按指数退避重试，
 *       由 {@link AiRetryUtil} 提供退避策略。</li>
 * </ol>
 *
 * <p>流式接口只做闸门 + 超时，<b>不重试</b>：重放一个已经开始吐字的流会让客户端
 * 收到重复内容，比直接报错更糟。
 *
 * @author ryu
 */
@Slf4j
@Component
@ConditionalOnAiEnabled
public class AiInvocationGuard {

    private final AiRetryUtil retryUtil;
    private final Semaphore semaphore;
    private final int maxConcurrent;

    public AiInvocationGuard(AiRetryUtil retryUtil,
                             @Value("${blog.ai.max-concurrent-requests:8}") int maxConcurrent) {
        this.retryUtil = retryUtil;
        this.maxConcurrent = Math.max(1, maxConcurrent);
        // fair=true：先进先出，避免某个请求被无限期饿死
        this.semaphore = new Semaphore(this.maxConcurrent, true);
        log.info("AI调用闸门已初始化: 最大并发={}", this.maxConcurrent);
    }

    /**
     * 执行一次 AI 调用（带并发闸门 + 超时 + 退避重试）
     *
     * @param supplier 调用工厂；每次重试都会重新调用它，从而真正重新发起请求
     * @param <T>      返回类型
     * @return 调用结果
     */
    public <T> Mono<T> call(Supplier<Mono<T>> supplier) {
        return Mono.defer(() -> {
            if (!semaphore.tryAcquire()) {
                log.warn("AI并发已达上限({})，拒绝新请求", maxConcurrent);
                return Mono.<T>error(BusinessException.systemBusy());
            }
            return retryUtil.withRetry(supplier.get())
                    .doFinally(signal -> semaphore.release());
        });
    }

    /**
     * 执行一次 AI 流式调用（带并发闸门；不重试）
     *
     * @param supplier 调用工厂
     * @return 内容片段流
     */
    public Flux<String> callStream(Supplier<Flux<String>> supplier) {
        return Flux.defer(() -> {
            if (!semaphore.tryAcquire()) {
                log.warn("AI并发已达上限({})，拒绝新流式请求", maxConcurrent);
                return Flux.error(BusinessException.systemBusy());
            }
            return supplier.get()
                    .doFinally(signal -> semaphore.release());
        });
    }

    /**
     * 当前可用的并发额度（用于监控）
     *
     * @return 可用许可数
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * 最大并发数
     *
     * @return 最大并发数
     */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }
}
