package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.repository.SysConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置写路径的缓存与事件护栏
 *
 * <p>{@code PUT /sysConfig/batch/update} 曾经<b>只写库</b>：既不驱逐缓存也不发
 * {@link ConfigChangeEvent}，于是通过批量更新改 AI Provider 配置，要等 12 小时缓存
 * 过期或重启才生效。这个用例把「每条更新的键都要驱逐 + 发事件」钉死。
 *
 * <p>另一个容易漏的是分组缓存：增删配置键会改变 {@code /sysConfig/groups} 的结果，
 * 但它的缓存键是固定值，和具体配置键无关，必须单独清。
 *
 * @author ryu
 */
class SysConfigBatchUpdateCacheTest {

    private SysConfigRepository repository;
    private CacheManager cacheManager;
    private Cache sysConfigCache;
    private Cache publicConfigCache;
    private ApplicationEventPublisher eventPublisher;
    private SysConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(SysConfigRepository.class);
        cacheManager = mock(CacheManager.class);
        sysConfigCache = mock(Cache.class);
        publicConfigCache = mock(Cache.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        when(cacheManager.getCache(CacheConstants.SYS_CONFIG_CACHE)).thenReturn(sysConfigCache);
        when(cacheManager.getCache(CacheConstants.PUBLIC_CONFIG_CACHE)).thenReturn(publicConfigCache);
        when(repository.save(any(SysConfig.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service = new SysConfigServiceImpl(repository, cacheManager, eventPublisher);
    }

    @Test
    @DisplayName("批量更新：每个键都驱逐实体缓存、清公开缓存、发变更事件")
    void batchUpdateEvictsAndPublishes() {
        SysConfig config = SysConfig.builder()
                .id(1L)
                .configKey("site.basic.name")
                .configValue("旧名字")
                .build();
        when(repository.findByConfigKey("site.basic.name")).thenReturn(Mono.just(config));

        service.batchUpdateConfig(Map.of("site.basic.name", "新名字")).block();

        verify(sysConfigCache).evict(CacheConstants.CONFIG_ENTITY_KEY_PREFIX + "site.basic.name");
        verify(sysConfigCache).evict(CacheConstants.CONFIG_ENTITY_BY_ID_KEY_PREFIX + 1L);
        // 公开键变更必须清空公开缓存，否则匿名调用方要等 5 分钟
        verify(publicConfigCache).clear();
        verify(eventPublisher, times(1)).publishEvent(any(ConfigChangeEvent.class));
    }

    @Test
    @DisplayName("批量更新敏感键：驱逐实体缓存，但不动公开缓存")
    void batchUpdateSensitiveKeyDoesNotTouchPublicCache() {
        SysConfig config = SysConfig.builder()
                .id(2L)
                .configKey("security.jwt.secret")
                .configValue("old")
                .build();
        when(repository.findByConfigKey("security.jwt.secret")).thenReturn(Mono.just(config));

        service.batchUpdateConfig(Map.of("security.jwt.secret", "new")).block();

        verify(sysConfigCache).evict(CacheConstants.CONFIG_ENTITY_KEY_PREFIX + "security.jwt.secret");
        verify(publicConfigCache, never()).clear();
        verify(eventPublisher).publishEvent(any(ConfigChangeEvent.class));
    }

    @Test
    @DisplayName("新增配置：驱逐分组缓存")
    void addConfigEvictsGroupsCache() {
        when(repository.existsByConfigKey(anyString())).thenReturn(Mono.just(false));

        SysConfigDTO dto = new SysConfigDTO();
        dto.setConfigKey("site.basic.copyright");
        dto.setConfigValue("© 2026");

        service.addConfig(dto).block();

        verify(sysConfigCache).evict(CacheConstants.CONFIG_GROUPS_KEY);
    }

    @Test
    @DisplayName("删除配置：驱逐分组缓存")
    void deleteConfigEvictsGroupsCache() {
        SysConfig config = SysConfig.builder()
                .id(3L)
                .configKey("site.basic.temp")
                .configValue("x")
                .build();
        when(repository.findById(3L)).thenReturn(Mono.just(config));
        when(repository.deleteById(3L)).thenReturn(Mono.empty());

        service.deleteConfig(3L).block();

        verify(sysConfigCache).evict(CacheConstants.CONFIG_GROUPS_KEY);
        verify(publicConfigCache).clear();
    }
}
