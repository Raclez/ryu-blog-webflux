package com.ryu.blog.strategy;

import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.repository.StorageConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存储配置管理器的读写与缓存驱逐。
 *
 * <p>原文件整份被注释掉、API 也早已过期（还是同步签名）。这里按当前的 Mono API 重写。
 *
 * @author ryu
 */
class StorageConfigManagerTest {

    private StorageConfigRepository repository;
    private CacheManager cacheManager;
    private Cache cache;
    private StorageConfigManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(StorageConfigRepository.class);
        cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(cache);

        manager = new StorageConfigManager(repository, event -> { }, cacheManager);
        // 自调用要经过 self 才会命中 @Cacheable；单元测试里没有代理，指向自己即可
        manager.setSelf(manager);
    }

    @Test
    @DisplayName("读配置属性：有值返回值，没值返回默认值")
    void getConfigProperty_readsValueOrFallsBack() {
        when(repository.findByStrategyKeyAndIsDeleted("local", false)).thenReturn(Mono.just(config("local")));

        assertEquals("/data/uploads",
                manager.getConfigPropertyAsync("local", "basePath", "/default").block());
        assertEquals("/default",
                manager.getConfigPropertyAsync("local", "missingKey", "/default").block(),
                "配置里没有这个键时必须返回默认值，而不是 null");
    }

    @Test
    @DisplayName("读访问地址：返回配置里的 accessUrl")
    void getAccessUrl_returnsConfiguredUrl() {
        when(repository.findByStrategyKeyAndIsDeleted("minio", false)).thenReturn(Mono.just(config("minio")));

        assertEquals("https://cdn.example.com", manager.getAccessUrlAsync("minio").block());
    }

    @Test
    @DisplayName("改配置属性：落库后驱逐缓存，避免读到旧值")
    void updateConfigProperty_evictsCache() {
        StorageConfig stored = config("local");
        when(repository.findByStrategyKeyAndIsDeleted("local", false)).thenReturn(Mono.just(stored));
        when(repository.save(org.mockito.ArgumentMatchers.any(StorageConfig.class)))
                .thenReturn(Mono.just(stored));

        manager.updateConfigProperty("local", "basePath", "/new/path").block();

        verify(cache, org.mockito.Mockito.atLeastOnce()).clear();
    }

    @Test
    @DisplayName("配置变更事件：storage 类型清缓存，其它类型不动")
    void onConfigChange_clearsOnlyForStorageType() {
        manager.onConfigChange(new ConfigChangeEvent(this, "storage", "local"));
        verify(cache, org.mockito.Mockito.atLeastOnce()).clear();

        // 清掉上一次的调用记录，否则 never() 会把第一次的 clear() 也算进来
        org.mockito.Mockito.clearInvocations(cache);

        manager.onConfigChange(new ConfigChangeEvent(this, "ai", "someKey"));
        verify(cache, never()).clear();
    }

    private StorageConfig config(String strategyKey) {
        StorageConfig config = new StorageConfig();
        config.setStrategyKey(strategyKey);
        config.setStrategyName(strategyKey.toUpperCase());
        Map<String, String> props = new HashMap<>();
        props.put("basePath", "/data/uploads");
        config.setConfigMap(props);
        config.setAccessUrl("https://cdn.example.com");
        return config;
    }
}
