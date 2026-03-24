package com.ryu.blog.strategy;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.repository.StorageConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
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

    // 当前激活的策略键
    private final AtomicReference<String> activeStrategyKey = new AtomicReference<>();
    
    // 防止事件循环的标志
    private final ThreadLocal<Boolean> isReloading = ThreadLocal.withInitial(() -> false);
    
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
            // 防止事件循环：如果正在重新加载配置，则忽略此事件
            if (isReloading.get()) {
                log.debug("正在重新加载配置，忽略配置变更事件: strategyKey={}", event.getConfigKey());
                return;
            }
            
            log.info("检测到存储配置变更: strategyKey={}", event.getConfigKey());
            
            // 根据事件更新当前活跃策略键
            String newStrategyKey = event.getConfigKey();
            if (newStrategyKey != null) {
                String oldKey = activeStrategyKey.getAndSet(newStrategyKey);
                log.info("更新当前活跃策略: {} (原策略: {})", newStrategyKey, oldKey);
            }
            
            // 清除配置缓存（不重新加载，避免循环）
            clearConfigCache();
        }
    }
    
    /**
     * 从数据库重新加载配置
     * @return 完成信号
     */
    public Mono<Void> reloadConfig() {
        // 防止重入
        if (isReloading.get()) {
            log.warn("配置正在重新加载中，跳过本次请求");
            return Mono.empty();
        }
        
        log.debug("开始重新加载存储配置");
        isReloading.set(true);
        
        return storageConfigRepository.findOneByIsEnableAndIsDeleted(true, false)
                .switchIfEmpty(Mono.defer((Supplier<Mono<StorageConfig>>) () -> {
                    log.warn("未找到启用的存储配置，尝试获取任意可用配置");
                    return storageConfigRepository.findAllByIsDeleted(false)
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
                .doFinally(signalType -> {
                    // 无论成功还是失败，都要清除重入标志
                    isReloading.set(false);
                })
                .then();
    }
    
    /**
     * 获取策略配置
     * @param strategyKey 策略键
     * @return 策略配置
     */
    @Cacheable(value = CacheConstants.STORAGE_CONFIG_CACHE, key = "#strategyKey", unless = "#result == null")
    public Mono<StorageConfig> getStrategyConfig(String strategyKey) {
        log.debug("获取策略配置: strategyKey={} (缓存未命中)", strategyKey);
        
        // 从数据库获取
        return storageConfigRepository.findByStrategyKeyAndIsDeleted(strategyKey, false)
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
    @CacheEvict(value = {CacheConstants.STORAGE_CONFIG_CACHE, CacheConstants.STORAGE_PROPERTIES_CACHE, CacheConstants.STORAGE_ACCESS_URL_CACHE}, allEntries = true)
    public void clearConfigCache() {
        log.info("存储配置缓存已清除");
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
    @Cacheable(value = CacheConstants.STORAGE_PROPERTIES_CACHE, key = "#strategyKey + ':' + #key", unless = "#result == null")
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
     * 获取配置属性集合
     * @param strategyKey 策略键
     * @return 配置属性集合的Mono
     */
    @Cacheable(value = CacheConstants.STORAGE_PROPERTIES_CACHE, key = "#strategyKey + CacheConstants.STORAGE_PROPERTIES_ALL_KEY", unless = "#result.isEmpty()")
    public Mono<Map<String, String>> getConfigPropertiesAsync(String strategyKey) {
        log.debug("获取配置属性集合: strategyKey={} (缓存未命中)", strategyKey);
        
        // 从配置对象获取属性集合
        return getStrategyConfig(strategyKey)
            .<Map<String, String>>map(config -> {
                Map<String, String> configProps = config.getConfigMap();
                log.debug("获取到配置属性集合: strategyKey={}, count={}", strategyKey, 
                    configProps != null ? configProps.size() : 0);
                return configProps != null ? configProps : new HashMap<>();
            })
            .defaultIfEmpty(new HashMap<>());
    }
    
    /**
     * 获取访问URL
     * @param strategyKey 策略键
     * @return 访问URL的Mono
     */
    @Cacheable(value = CacheConstants.STORAGE_ACCESS_URL_CACHE, key = "#strategyKey", unless = "#result == null or #result.isEmpty()")
    public Mono<String> getAccessUrlAsync(String strategyKey) {
        log.debug("获取访问URL: strategyKey={} (缓存未命中)", strategyKey);
        
        // 从配置对象获取访问URL
        return getStrategyConfig(strategyKey)
            .map(config -> {
                String accessUrl = config.getAccessUrl();
                log.debug("获取到访问URL: strategyKey={}, accessUrl={}", strategyKey, accessUrl);
                return accessUrl;
            });
    }
    
    /**
     * 更新配置属性
     * @param strategyKey 策略键
     * @param key 属性键
     * @param value 属性值
     * @return Void Mono
     */
    @CacheEvict(value = {CacheConstants.STORAGE_PROPERTIES_CACHE}, key = "#strategyKey + ':' + #key")
    public Mono<Void> updateConfigProperty(String strategyKey, String key, String value) {
        log.info("更新配置属性: strategyKey={}, key={}, value={}", strategyKey, key, value);
        
        // 更新属性
        return getStrategyConfig(strategyKey)
            .flatMap(config -> {
                Map<String, String> configProps = config.getConfigMap();
                if (configProps == null) {
                    configProps = new HashMap<>();
                    config.setConfigMap(configProps);
                }
                
                // 更新属性值
                configProps.put(key, value);
                config.setUpdateTime(LocalDateTime.now());
                
                // 保存到数据库
                return storageConfigRepository.save(config)
                    .doOnNext(savedConfig -> log.info("配置属性已更新: strategyKey={}, key={}", strategyKey, key))
                    .then();
            });
    }
    
    /**
     * 更新配置属性集合
     * @param strategyKey 策略键
     * @param properties 属性集合
     * @return Void Mono
     */
    @CacheEvict(value = {CacheConstants.STORAGE_PROPERTIES_CACHE}, key = "#strategyKey + CacheConstants.STORAGE_PROPERTIES_ALL_KEY")
    public Mono<Void> updateConfigProperties(String strategyKey, Map<String, String> properties) {
        log.info("更新配置属性集合: strategyKey={}, propertiesCount={}", strategyKey, properties != null ? properties.size() : 0);
        
        // 更新属性集合
        return getStrategyConfig(strategyKey)
            .flatMap(config -> {
                Map<String, String> configProps = config.getConfigMap();
                if (configProps == null) {
                    configProps = new HashMap<>();
                    config.setConfigMap(configProps);
                }
                
                // 更新所有属性值
                if (properties != null) {
                    configProps.putAll(properties);
                }
                
                config.setUpdateTime(LocalDateTime.now());
                
                // 保存到数据库
                return storageConfigRepository.save(config)
                    .doOnNext(savedConfig -> log.info("配置属性集合已更新: strategyKey={}", strategyKey))
                    .then();
            });
    }
} 