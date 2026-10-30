package com.ryu.blog.service.impl.file;

import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 一个「够真」的 Redis 替身：同时支持 String 值与 Hash 两种结构。
 *
 * <p>分片会话拆成两类 key 之后（会话对象 + 分片状态 Hash），
 * 只桩 {@code opsForValue} 的假 Redis 会让 {@code opsForHash()} 返回 null。
 *
 * @author ryu
 */
final class FakeMultipartRedis {

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> hashes = new ConcurrentHashMap<>();

    private FakeMultipartRedis() {
    }

    /**
     * 建一个独立的假 Redis（单实例测试用）。
     *
     * @return 模板
     */
    static ReactiveRedisTemplate<String, Object> create() {
        return new FakeMultipartRedis().template();
    }

    /**
     * 建一个可共享的存储：对同一个实例多次调用 {@link #template()}，
     * 得到的是不同模板、同一份底层数据 —— 用来模拟「多个服务实例连同一个 Redis」。
     *
     * @return 共享存储
     */
    static FakeMultipartRedis shared() {
        return new FakeMultipartRedis();
    }

    @SuppressWarnings("unchecked")
    ReactiveRedisTemplate<String, Object> template() {
        ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);

        ReactiveValueOperations<String, Object> valueOps = mock(ReactiveValueOperations.class);
        when(valueOps.set(anyString(), any(), any(Duration.class))).thenAnswer(inv -> {
            values.put(inv.getArgument(0), inv.getArgument(1));
            return Mono.just(true);
        });
        when(valueOps.get(anyString())).thenAnswer(inv -> Mono.justOrEmpty(values.get(inv.getArgument(0))));
        when(redis.opsForValue()).thenReturn(valueOps);

        ReactiveHashOperations<String, String, Object> hashOps = mock(ReactiveHashOperations.class);
        when(hashOps.put(anyString(), anyString(), any())).thenAnswer(inv -> {
            hashes.computeIfAbsent(inv.getArgument(0), key -> new ConcurrentHashMap<>())
                    .put(inv.getArgument(1), inv.getArgument(2));
            return Mono.just(true);
        });
        when(hashOps.entries(anyString())).thenAnswer(inv -> {
            Map<String, Object> hash = hashes.getOrDefault(inv.getArgument(0), Map.of());
            return Flux.fromIterable(hash.entrySet())
                    .map(entry -> (Map.Entry<Object, Object>) new AbstractMap.SimpleEntry<Object, Object>(
                            entry.getKey(), entry.getValue()));
        });
        when(redis.<String, Object>opsForHash()).thenReturn(hashOps);

        when(redis.delete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            values.remove(key);
            hashes.remove(key);
            return Mono.just(1L);
        });

        return redis;
    }
}
