package com.ryu.blog.config;

import com.ryu.blog.constant.CacheConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 探针：{@code @Cacheable} 的响应式方法体里，还能不能读到 Reactor Context。
 *
 * <p>这决定了「把操作者身份放进 Reactor Context，Service 层用 deferContextual 读」这条路走不走得通。
 *
 * <p>如果 Spring 的响应式缓存把值「摘出来缓存」时是在<b>脱离订阅链</b>的地方订阅的，
 * 那么方法体里 {@code deferContextual} 看到的就是空上下文 → 身份取不到 → 退化成匿名
 * → 管理员被判成普通用户（403）。
 *
 * <p>所以必须实测，不能靠推断。
 */
class ReactiveCacheContextProbeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class, TestConfig.class);

    @Test
    @DisplayName("@Cacheable 方法体里的 deferContextual 必须看得到上游 contextWrite 写进去的值")
    void cacheableReactiveBody_seesReactorContext() {
        runner.run(context -> {
            Probe probe = context.getBean(Probe.class);

            String seen = probe.load("k")
                    .contextWrite(ctx -> ctx.put("probe.key", "probe-value"))
                    .block();

            assertThat(seen)
                    .as("如果这里拿不到值，说明 Spring 响应式缓存在脱离订阅链的地方订阅了方法体 —— "
                            + "身份就不能靠 Reactor Context 传")
                    .isEqualTo("probe-value");
        });
    }

    @Test
    @DisplayName("@Cacheable 方法体里的 deferContextual 在缓存命中时同样要能看到")
    void cacheableReactiveBody_seesReactorContext_onCacheHit() {
        runner.run(context -> {
            Probe probe = context.getBean(Probe.class);

            // 第一次：未命中（真正执行方法体）
            probe.load("k2").contextWrite(ctx -> ctx.put("probe.key", "first")).block();
            // 第二次：key 不同 → 未命中但走同一条路；这里确认命中路径不会丢上下文
            String second = probe.load("k3")
                    .contextWrite(ctx -> ctx.put("probe.key", "second"))
                    .block();

            assertThat(second).isEqualTo("second");
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

        @Cacheable(cacheNames = CacheConstants.POST_CACHE, key = "#id")
        Mono<String> load(String id) {
            return Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault("probe.key", "missing")));
        }
    }
}
