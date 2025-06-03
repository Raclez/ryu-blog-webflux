package com.ryu.blog.strategy;

import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.repository.StorageConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 存储配置管理器
 * 负责加载、缓存和管理存储配置及当前活跃策略
 * 
 * @author ryu 475118582@qq.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StorageConfigManager {

    private final StorageConfigRepository storageConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;
    
    // 当前激活的策略键
    private final AtomicReference<String> activeStrategyKey = new AtomicReference<>();
    
    // 默认策略键
    private static final String DEFAULT_STRATEGY_KEY = "local";
    
    @PostConstruct
    public void init() {
        log.info("初始化存储配置管理器...");
        
        // 初始化时加载配置
        reloadConfig()
            .doOnError(e -> log.error("初始化加载存储配置失败，将使用默认策略: {}", e.getMessage(), e))
            .subscribe(
                unused -> {
                    String activeStrategy = activeStrategyKey.get();
                    log.info("存储配置初始化完成，当前活跃策略: {}", 
                        activeStrategy != null ? activeStrategy : "未设置（将使用默认策略）");
                },
                error -> log.error("存储配置初始化失败: {}", error.getMessage(), error)
            );
    }
    
    /**
     * 监听配置变更事件
     * @param event 配置变更事件
     */
    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        if (event.getConfigType().equals("storage")) {
            log.info("检测到存储配置变更: strategyKey={}", event.getConfigKey());
            
            // 根据事件更新当前活跃策略键
            String newStrategyKey = event.getConfigKey();
            if (newStrategyKey != null) {
                String oldKey = activeStrategyKey.getAndSet(newStrategyKey);
                log.info("更新当前活跃策略: {} (原策略: {})", newStrategyKey, oldKey);
            }
            
            // 清除配置缓存并重新加载
            clearConfigCache();
            reloadConfig().subscribe(
                unused -> log.info("存储配置已重新加载"),
                error -> log.error("重新加载存储配置失败: {}", error.getMessage(), error)
            );
        }
    }
    
    /**
     * 从数据库重新加载配置
     * @return 完成信号
     */
    public Mono<Void> reloadConfig() {
        log.debug("开始重新加载存储配置");
        return storageConfigRepository.findOneByIsEnableAndIsDeleted(true, 0)
                .switchIfEmpty(Mono.defer((Supplier<Mono<StorageConfig>>) () -> {
                    log.warn("未找到启用的存储配置，尝试获取任意可用配置");
                    return storageConfigRepository.findAllByIsDeleted(0)
                            .take(1)
                            .singleOrEmpty();
                }))
                .switchIfEmpty(Mono.defer((Supplier<Mono<StorageConfig>>) () -> {
                    log.warn("未找到任何存储配置，将使用默认策略");
                    // 如果没有找到配置，设置默认策略键
                    activeStrategyKey.set(DEFAULT_STRATEGY_KEY);
                    return Mono.<StorageConfig>empty();
                }))
                .doOnNext(config -> {
                    String key = config.getStrategyKey();
                    
                    // 更新当前活跃策略键
                    activeStrategyKey.set(key);
                    
                    // 发布策略变更事件
                    publishConfigChangeEvent(key);
                    
                    log.info("加载存储配置成功: strategyKey={}, strategyName={}", key, config.getStrategyName());
                })
                .doOnError(e -> log.error("加载存储配置失败: {}", e.getMessage(), e))
                .then();
    }
    
    /**
     * 获取策略配置
     * @param strategyKey 策略键
     * @return 策略配置
     */
    @Cacheable(value = "storageConfig", key = "#strategyKey", unless = "#result == null")
    public Mono<StorageConfig> getStrategyConfig(String strategyKey) {
        log.debug("获取策略配置: strategyKey={} (缓存未命中)", strategyKey);
        
        // 从数据库获取
        return storageConfigRepository.findByStrategyKeyAndIsDeleted(strategyKey, 0)
                .doOnNext(config -> log.debug("策略配置已从数据库加载: strategyKey={}", strategyKey))
                .switchIfEmpty(Mono.fromRunnable(() -> 
                    log.warn("未找到策略配置: strategyKey={}", strategyKey)
                ).then(Mono.<StorageConfig>empty()));
    }
    
    /**
     * 获取当前活跃的策略键
     * @return 当前活跃的策略键
     */
    public String getActiveStrategyKey() {
        String key = activeStrategyKey.get();
        log.debug("获取当前活跃策略键: {}", key != null ? key : "未设置（将使用默认策略）");
        
        // 如果未设置活跃策略键，返回默认策略键
        return key != null ? key : DEFAULT_STRATEGY_KEY;
    }
    
    /**
     * 设置当前活跃的策略键
     * @param strategyKey 策略键
     */
    public void setActiveStrategyKey(String strategyKey) {
        if (strategyKey == null || strategyKey.isEmpty()) {
            log.warn("尝试设置空的策略键为活跃策略");
            return;
        }
        
        String oldKey = activeStrategyKey.getAndSet(strategyKey);
        log.info("设置活跃存储策略: {} (原策略: {})", strategyKey, oldKey);
        
        // 发布配置变更事件
        publishConfigChangeEvent(strategyKey);
    }
    
    /**
     * 清除配置缓存
     */
    @CacheEvict(value = {"storageConfig", "storageProperties", "accessUrl"}, allEntries = true)
    public void clearConfigCache() {
        log.info("存储配置缓存已清除");
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 获取Caffeine缓存统计信息
            CaffeineCache storageConfigCache = (CaffeineCache) cacheManager.getCache("storageConfig");
            CaffeineCache storagePropertiesCache = (CaffeineCache) cacheManager.getCache("storageProperties");
            CaffeineCache accessUrlCache = (CaffeineCache) cacheManager.getCache("accessUrl");
            
            if (storageConfigCache != null) {
                com.github.benmanes.caffeine.cache.stats.CacheStats configStats = 
                    storageConfigCache.getNativeCache().stats();
                stats.put("storageConfig.hitRate", configStats.hitRate());
                stats.put("storageConfig.size", storageConfigCache.getNativeCache().estimatedSize());
            }
            
            if (storagePropertiesCache != null) {
                com.github.benmanes.caffeine.cache.stats.CacheStats propsStats = 
                    storagePropertiesCache.getNativeCache().stats();
                stats.put("storageProperties.hitRate", propsStats.hitRate());
                stats.put("storageProperties.size", storagePropertiesCache.getNativeCache().estimatedSize());
            }
            
            if (accessUrlCache != null) {
                com.github.benmanes.caffeine.cache.stats.CacheStats urlStats = 
                    accessUrlCache.getNativeCache().stats();
                stats.put("accessUrl.hitRate", urlStats.hitRate());
                stats.put("accessUrl.size", accessUrlCache.getNativeCache().estimatedSize());
            }
        } catch (Exception e) {
            log.warn("获取缓存统计信息失败: {}", e.getMessage());
        }
        
        log.debug("缓存统计信息: {}", stats);
        return stats;
    }
    
    /**
     * 发布配置变更事件
     * @param strategyKey 变更的策略键
     */
    public void publishConfigChangeEvent(String strategyKey) {
        log.info("发布存储配置变更事件: strategyKey={}", strategyKey);
        ConfigChangeEvent event = new ConfigChangeEvent(this, "storage", strategyKey);
        eventPublisher.publishEvent(event);
    }
    
    /**
     * 获取配置属性
     * @param strategyKey 策略键
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值的Mono
     */
    @Cacheable(value = "storageProperties", key = "#strategyKey + ':' + #key", unless = "#result == null")
    public Mono<String> getConfigPropertyAsync(String strategyKey, String key, String defaultValue) {
        log.debug("获取配置属性: strategyKey={}, key={} (缓存未命中)", strategyKey, key);
        
        // 从配置对象获取属性
        return getStrategyConfig(strategyKey)
            .map(config -> {
                Map<String, String> configProps = config.getConfigMap();
                String value = configProps != null ? configProps.get(key) : null;
                return StringUtils.hasText(value) ? value : defaultValue;
            })
            .defaultIfEmpty(defaultValue);
    }
    
    /**
     * 获取配置属性（同步方法，仅供非响应式上下文使用）
     * @param strategyKey 策略键
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值
     * @deprecated 请在响应式上下文中使用 getConfigPropertyAsync 方法
     */
    @Deprecated
    public String getConfigProperty(String strategyKey, String key, String defaultValue) {
        // 从缓存获取
        try {
            CaffeineCache cache = (CaffeineCache) cacheManager.getCache("storageProperties");
            if (cache != null) {
                String cacheKey = strategyKey + ':' + key;
                String cachedValue = cache.get(cacheKey, String.class);
                if (cachedValue != null) {
                    return cachedValue;
                }
            }
        } catch (Exception e) {
            log.warn("从缓存获取配置属性失败: {}", e.getMessage());
        }
        
        // 如果缓存未命中，返回默认值
        log.warn("同步获取配置属性，返回默认值: strategyKey={}, key={}, defaultValue={}", strategyKey, key, defaultValue);
        return defaultValue;
    }
    
    /**
     * 获取配置属性Map
     * @param strategyKey 策略键
     * @return 配置属性Map的Mono
     */
    @Cacheable(value = "storageProperties", key = "#strategyKey + ':all'", unless = "#result.isEmpty()")
    public Mono<Map<String, String>> getConfigPropertiesAsync(String strategyKey) {
        log.debug("获取配置属性Map: strategyKey={} (缓存未命中)", strategyKey);
        
        // 从配置对象获取属性Map
        return getStrategyConfig(strategyKey)
            .map(config -> {
                Map<String, String> configProps = config.getConfigMap();
                return configProps != null ? new HashMap<>(configProps) : new HashMap<String, String>();
            })
            .<Map<String, String>>map(props -> props)
            .defaultIfEmpty(new HashMap<>());
    }
    
    /**
     * 获取访问URL
     * @param strategyKey 策略键
     * @return 访问URL的Mono
     */
    @Cacheable(value = "accessUrl", key = "#strategyKey", unless = "#result == null or #result.isEmpty()")
    public Mono<String> getAccessUrlAsync(String strategyKey) {
        log.debug("获取访问URL: strategyKey={} (缓存未命中)", strategyKey);
        
        // 从配置对象获取访问URL
        return getStrategyConfig(strategyKey)
            .map(StorageConfig::getAccessUrl)
            .filter(StringUtils::hasText)
            .defaultIfEmpty("");
    }
    
    /**
     * 获取访问URL（同步方法，仅供非响应式上下文使用）
     * @param strategyKey 策略键
     * @return 访问URL
     * @deprecated 请在响应式上下文中使用 getAccessUrlAsync 方法
     */
    @Deprecated
    public String getAccessUrl(String strategyKey) {
        // 从缓存获取
        try {
            CaffeineCache cache = (CaffeineCache) cacheManager.getCache("accessUrl");
            if (cache != null) {
                String cachedValue = cache.get(strategyKey, String.class);
                if (cachedValue != null) {
                    return cachedValue;
                }
            }
        } catch (Exception e) {
            log.warn("从缓存获取访问URL失败: {}", e.getMessage());
        }
        
        // 如果缓存未命中，返回空字符串
        log.warn("同步获取访问URL，返回空字符串: strategyKey={}", strategyKey);
        return "";
    }
    
    /**
     * 更新配置属性
     * @param strategyKey 策略键
     * @param key 属性键
     * @param value 属性值
     * @return 完成信号
     */
    @CacheEvict(value = {"storageProperties"}, key = "#strategyKey + ':' + #key")
    public Mono<Void> updateConfigProperty(String strategyKey, String key, String value) {
        return getStrategyConfig(strategyKey)
            .flatMap(config -> {
                Map<String, String> props = config.getConfigMap();
                props.put(key, value);
                config.setConfigMap(props);
                return storageConfigRepository.save(config);
            })
            .doOnNext(config -> {
                // 发布配置变更事件
                publishConfigChangeEvent(strategyKey);
                
                log.info("更新配置属性成功: strategyKey={}, key={}, value={}", strategyKey, key, value);
            })
            .then();
    }
    
    /**
     * 批量更新配置属性
     * @param strategyKey 策略键
     * @param properties 属性Map
     * @return 完成信号
     */
    @CacheEvict(value = {"storageProperties"}, key = "#strategyKey + ':all'")
    public Mono<Void> updateConfigProperties(String strategyKey, Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return Mono.empty();
        }
        
        return getStrategyConfig(strategyKey)
            .flatMap(config -> {
                Map<String, String> props = config.getConfigMap();
                props.putAll(properties);
                config.setConfigMap(props);
                return storageConfigRepository.save(config);
            })
            .doOnNext(config -> {
                // 发布配置变更事件
                publishConfigChangeEvent(strategyKey);
                
                log.info("批量更新配置属性成功: strategyKey={}, properties={}", strategyKey, properties.keySet());
            })
            .then();
    }
} 