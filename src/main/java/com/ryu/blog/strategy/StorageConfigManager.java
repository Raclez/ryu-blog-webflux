package com.ryu.blog.strategy;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.StorageConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /**
     * 是否已经从数据库成功读到过「启用中」的配置。
     *
     * <p>用它把「配置真的加载成功」和「悄悄退回了 local」区分开 ——
     * 早先两者在外部看来完全一样，管理员以为用的是 OSS，
     * 实际文件全写在本地磁盘上。
     */
    private volatile boolean ready = false;

    // 重入标志：只用于阻止「并发重复加载」，不参与事件处理。
    // 原来用的是 ThreadLocal，而标志在「组装期」置位、在「订阅期」复位，
    // 两者往往不在同一个线程上，导致调用线程上的标志永久为 true，
    // 之后的 reloadConfig() 全部被短路 —— 配置再也读不到新值。
    private final AtomicBoolean reloading = new AtomicBoolean(false);

    // 默认策略键
    private static final String DEFAULT_STRATEGY_KEY = "local";

    // 注入自身代理，让类内部对 getStrategyConfig 的调用也能命中 @Cacheable。
    // 自调用会绕过 Spring 代理，缓存会静默失效 —— 每次都直接打数据库。
    private StorageConfigManager self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy StorageConfigManager self) {
        this.self = self;
    }
    
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
        if (!"storage".equals(event.getConfigType())) {
            return;
        }

        log.info("检测到存储配置变更: strategyKey={}", event.getConfigKey());

        // 清除配置缓存。
        // 注意：这里不能无条件把事件里的 key 设成活跃策略 ——
        // 事件也会在「策略被停用 / 删除」时发出，那会把已停用的策略推成活跃策略。
        // 活跃策略的权威来源始终是数据库中 is_enable = 1 的那一行，由 reloadConfig() 负责同步。
        clearConfigCache();
    }
    
    /**
     * 从数据库重新加载配置
     * @return 完成信号
     */
    public Mono<Void> reloadConfig() {
        // 重入标志必须在「订阅期」置位、在同一个 doFinally 里复位，
        // 否则组装期置位 / 订阅期复位的组合会让标志永远留在 true 上。
        return Mono.defer(() -> {
            if (!reloading.compareAndSet(false, true)) {
                // 不能返回 Mono.empty()：管理员连点两次「刷新配置」，
                // 第二次会得到一个看起来成功、实际什么都没做的信号
                log.warn("配置正在重新加载中，拒绝本次请求");
                return Mono.error(new BusinessException("存储配置正在重新加载，请稍后重试"));
            }

            log.debug("开始重新加载存储配置");

            return storageConfigRepository.findOneByIsEnableAndIsDeleted(true, false)
                    .switchIfEmpty(Mono.defer((Supplier<Mono<StorageConfig>>) () -> {
                        // 没有启用中的配置：明确报错，并标记「未就绪」。
                        // 早先这里是静默退回 local —— 管理员以为在用对象存储，
                        // 实际文件全落在本地磁盘上，而且没有任何一处会告诉他。
                        // 注意仍然不能退回「任意一条配置」：那可能是已停用的策略。
                        ready = false;
                        log.error("未找到启用的存储配置：无法确定活跃存储策略（不会静默退回 {}）",
                                DEFAULT_STRATEGY_KEY);
                        return Mono.error(new BusinessException(
                                "未找到启用的存储配置，请先在系统配置中启用一个存储策略"));
                    }))
                    .doOnNext(config -> {
                        String key = config.getStrategyKey();

                        // 更新当前活跃策略键
                        activeStrategyKey.set(key);
                        ready = true;

                        // 发布策略变更事件
                        publishConfigChangeEvent(key);

                        log.info("加载存储配置成功: strategyKey={}, strategyName={}", key, config.getStrategyName());
                    })
                    .doOnError(e -> {
                        ready = false;
                        log.error("加载存储配置失败", e);
                    })
                    .doFinally(signalType -> {
                        // 无论成功还是失败，都要清除重入标志
                        reloading.set(false);
                    })
                    .then();
        });
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
     * 是否已经成功加载过启用中的存储配置。
     *
     * <p>{@code false} 意味着当前用的是兜底默认策略，而不是管理员配置的那个。
     *
     * @return 已就绪返回 true
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * 获取当前活跃的策略键
     * @return 当前活跃的策略键
     */
    public String getActiveStrategyKey() {
        String key = activeStrategyKey.get();
        if (key == null) {
            log.warn("尚未加载到启用的存储配置，退回到默认策略: {}", DEFAULT_STRATEGY_KEY);
        } else {
            log.debug("获取当前活跃策略键: {}", key);
        }
        
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
     * <p>
     * 这里直接操作 {@link CacheManager}，而不是靠 {@code @CacheEvict} 注解：
     * 该方法在本类内部被调用（自调用），会绕过 Spring 代理，
     * 注解形式的缓存驱逐根本不会执行 —— 缓存会一直留着旧值直到 TTL 过期。
     */
    public void clearConfigCache() {
        for (String cacheName : List.of(
                CacheConstants.STORAGE_CONFIG_CACHE,
                CacheConstants.STORAGE_PROPERTIES_CACHE,
                CacheConstants.STORAGE_ACCESS_URL_CACHE)) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            } else {
                log.warn("存储配置缓存不存在: {}", cacheName);
            }
        }
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
        return self.getStrategyConfig(strategyKey)
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
    @Cacheable(value = CacheConstants.STORAGE_PROPERTIES_CACHE, key = "#strategyKey + '" + CacheConstants.STORAGE_PROPERTIES_ALL_KEY_PREFIX + "'", unless = "#result.isEmpty()")
    public Mono<Map<String, String>> getConfigPropertiesAsync(String strategyKey) {
        log.debug("获取配置属性集合: strategyKey={} (缓存未命中)", strategyKey);
        
        // 从配置对象获取属性集合
        return self.getStrategyConfig(strategyKey)
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
        return self.getStrategyConfig(strategyKey)
            .map(config -> {
                String accessUrl = config.getAccessUrl();
                log.debug("获取到访问URL: strategyKey={}, accessUrl={}", strategyKey, accessUrl);
                return accessUrl;
            });
    }
    
    /**
     * 更新配置属性
     *
     * <p><b>必须同时驱逐 {@link CacheConstants#STORAGE_CONFIG_CACHE}</b>：
     * {@code getStrategyConfig} 缓存的是<b>整个</b> {@code StorageConfig}
     * （含 {@code configMap}），而 {@code getConfigPropertiesAsync} /
     * {@code getConfigPropertyAsync} 都是从那份缓存里读 {@code configMap} 的。
     * 只驱逐 {@code STORAGE_PROPERTIES_CACHE} 的话，
     * 缓存里的 {@code configMap} 还是旧的 —— <b>属性更新被静默丢弃</b>：
     * 数据库里已经是新值，读回来的却还是旧值。
     *
     * @param strategyKey 策略键
     * @param key 属性键
     * @param value 属性值
     * @return Void Mono
     */
    @CacheEvict(value = {CacheConstants.STORAGE_PROPERTIES_CACHE, CacheConstants.STORAGE_CONFIG_CACHE},
            allEntries = true)
    public Mono<Void> updateConfigProperty(String strategyKey, String key, String value) {
        // 值可能是 AK/SK —— 不能进日志
        log.info("更新配置属性: strategyKey={}, key={}", strategyKey, key);

        // 更新属性。
        // 这里刻意不走缓存：@Cacheable 返回的是同一个实例，
        // 直接改会把缓存里的对象一起改掉（保存失败时缓存里就留下了未落库的脏数据）。
        return storageConfigRepository.findByStrategyKeyAndIsDeleted(strategyKey, false)
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
                    // 落库成功后必须驱逐缓存：读路径是 @Cacheable 的，
                    // 不清的话接下来最长一个 TTL 内读到的都是旧值 ——
                    // 管理员改了 basePath，上传却还写到旧目录。
                    .then(Mono.fromRunnable(() -> {
                        clearConfigCache();
                        publishConfigChangeEvent(strategyKey);
                    }));
            });
    }

    /**
     * 更新配置属性集合
     *
     * <p>同样要驱逐 {@link CacheConstants#STORAGE_CONFIG_CACHE} —— 理由见
     * {@link #updateConfigProperty}。
     *
     * @param strategyKey 策略键
     * @param properties 属性集合
     * @return Void Mono
     */
    @CacheEvict(value = {CacheConstants.STORAGE_PROPERTIES_CACHE, CacheConstants.STORAGE_CONFIG_CACHE},
            allEntries = true)
    public Mono<Void> updateConfigProperties(String strategyKey, Map<String, String> properties) {
        log.info("更新配置属性集合: strategyKey={}, propertiesCount={}", strategyKey, properties != null ? properties.size() : 0);

        // 更新属性集合（同上：写路径不走缓存，避免改动缓存实例）
        return storageConfigRepository.findByStrategyKeyAndIsDeleted(strategyKey, false)
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
                        .doOnNext(saved -> log.info("配置属性集合已更新: strategyKey={}", strategyKey))
                        // 同上：写成功必须驱逐读缓存
                        .then(Mono.fromRunnable(() -> {
                            clearConfigCache();
                            publishConfigChangeEvent(strategyKey);
                        }));
            });
    }
} 