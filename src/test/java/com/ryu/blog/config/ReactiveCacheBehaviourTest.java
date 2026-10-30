package com.ryu.blog.config;

import com.ryu.blog.constant.CacheConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 验证「在响应式方法上使用 {@code @Cacheable}」的实际行为。
 *
 * <p>项目里 25+ 个 {@code @Cacheable} 方法返回的都是 {@code Mono}/{@code Flux}。
 * Spring Framework 6.1 的 {@code CacheAspectSupport} 内置了 {@code ReactiveCachingHandler}，
 * 能识别响应式返回类型，缓存里存的是**解析后的值**而不是 Publisher 本身。
 *
 * <p>但它有两个硬前提，本测试把它们锁死：
 * <ol>
 *   <li>缓存管理器必须是 {@code AsyncCache}（{@code CaffeineCacheManager.setAsyncCacheMode(true)}），
 *       否则所有响应式 {@code @Cacheable} 方法直接抛
 *       {@code IllegalStateException: No Caffeine AsyncCache available}；</li>
 *   <li>{@code unless} 表达式里的 {@code #result} 是**解析后的值**，
 *       所以 {@code #result.isEmpty()} 用在 {@code Mono<List<T>>} 上是合法的
 *       （{@code List} 有 {@code isEmpty()}，{@code Mono} 没有）。</li>
 * </ol>
 *
 * @author ryu
 */
class ReactiveCacheBehaviourTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class, TestConfig.class);

    @Test
    void cacheManagerMustBeAsyncCapableForReactiveCaching() {
        runner.run(context -> {
            // 项目真实的 CacheConfig 必须开启异步缓存模式，
            // 否则 CacheAspectSupport 的 ReactiveCachingHandler 直接抛
            // "No Caffeine AsyncCache available"。
            assertThat(context).hasNotFailed();
            Probe probe = context.getBean(Probe.class);

            assertThatCode(() -> probe.load("k").block()).doesNotThrowAnyException();
        });
    }

    @Test
    void cacheStoresTheResolvedValue_notThePublisher() {
        runner.run(context -> {
            Probe probe = context.getBean(Probe.class);

            // 第一次：真正的加载
            assertThat(probe.load("k").block()).isEqualTo("value-k");
            assertThat(probe.invocationCount()).isEqualTo(1);

            // 第二次：命中缓存
            assertThat(probe.load("k").block()).isEqualTo("value-k");

            // 缓存里存的是解析后的值，不是 Publisher 本身，
            // 所以底层加载器只会被调用一次。
            assertThat(probe.invocationCount())
                    .as("缓存命中后底层加载器不应被再次调用")
                    .isEqualTo(1);
        });
    }

    @Test
    void unlessIsEvaluatedAgainstTheResolvedValue() {
        runner.run(context -> {
            Probe probe = context.getBean(Probe.class);

            // 空列表：unless 命中 → 不入缓存 → 每次都重新加载
            assertThat(probe.loadList("empty").block()).isEmpty();
            assertThat(probe.loadList("empty").block()).isEmpty();
            assertThat(probe.invocationCount()).isEqualTo(2);

            // 非空列表：unless 不命中 → 入缓存 → 第二次命中缓存
            assertThat(probe.loadList("full").block()).containsExactly("a");
            assertThat(probe.loadList("full").block()).containsExactly("a");
            assertThat(probe.invocationCount())
                    .as("非空结果应被缓存，第二次不应重新加载")
                    .isEqualTo(3);
        });
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    @Component
    static class Probe {

        private final AtomicInteger invocations = new AtomicInteger();

        @Cacheable(cacheNames = CacheConstants.POST_CACHE, key = "#id")
        Mono<String> load(String id) {
            invocations.incrementAndGet();
            return Mono.just("value-" + id);
        }

        @Cacheable(cacheNames = CacheConstants.POST_FRONT_CACHE, key = "#id",
                unless = "#result.isEmpty()")
        Mono<List<String>> loadList(String id) {
            invocations.incrementAndGet();
            return Mono.just("empty".equals(id) ? List.of() : List.of("a"));
        }

        /**
         * 走方法调用而不是字段访问 —— Probe 会被 CGLIB 代理，
         * 代理实例上的字段是 null（字段初始化器不会在代理上跑）。
         */
        int invocationCount() {
            return invocations.get();
        }
    }
}
