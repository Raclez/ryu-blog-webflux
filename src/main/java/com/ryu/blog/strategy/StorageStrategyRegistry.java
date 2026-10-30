package com.ryu.blog.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储策略注册表
 * 负责注册和获取存储策略实现
 * 
 * @author ryu 475118582@qq.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StorageStrategyRegistry {
    
    private final List<FileStorageStrategy> strategies;
    private final StorageConfigManager configManager;
    
    // 缓存策略实例
    private final Map<String, FileStorageStrategy> strategyMap = new ConcurrentHashMap<>();
    
    // 默认策略键
    private static final String DEFAULT_STRATEGY_KEY = "local";
    
    @PostConstruct
    public void init() {
        log.info("初始化存储策略注册表...");
        
        // 将所有策略放入Map中
        for (FileStorageStrategy strategy : strategies) {
            String key = strategy.getStrategyKey();
            strategyMap.put(key, strategy);
            log.info("注册存储策略: {}", key);
        }
        
        log.info("存储策略注册完成，共注册{}个策略: {}", strategyMap.size(), strategyMap.keySet());
        
        // 确保默认策略存在
        if (!strategyMap.containsKey(DEFAULT_STRATEGY_KEY)) {
            log.warn("默认存储策略 '{}' 不存在，系统可能无法正常工作", DEFAULT_STRATEGY_KEY);
        } else {
            log.info("默认存储策略 '{}' 已就绪", DEFAULT_STRATEGY_KEY);
        }
    }
    
    /**
     * 获取当前活跃的存储策略
     * @return 存储策略
     */
    public Mono<FileStorageStrategy> getActiveStrategy() {
        // 从配置管理器获取当前活跃策略键
        String activeKey = configManager.getActiveStrategyKey();
        
        // 如果已有活跃策略且存在，直接返回
        if (activeKey != null && strategyMap.containsKey(activeKey)) {
            log.debug("获取当前活跃策略: {}", activeKey);
            return getStrategyWithFallback(activeKey);
        }
        
        // 如果没有设置活跃策略，返回默认策略
        log.debug("未设置活跃策略，使用默认策略: {}", DEFAULT_STRATEGY_KEY);
        return getStrategyWithFallback(DEFAULT_STRATEGY_KEY);
    }
    
    /**
     * 获取指定的存储策略
     * @param strategyKey 策略标识
     * @return 存储策略
     */
    public Mono<FileStorageStrategy> getStrategy(String strategyKey) {
        log.debug("获取存储策略: {}", strategyKey);

        // ConcurrentHashMap.containsKey(null) 直接抛 NPE ——
        // 策略标识为空的行会走进这里（历史的 t_file.storage_type 可能是 NULL）。
        if (strategyKey == null || strategyKey.isEmpty()) {
            log.warn("存储策略标识为空");
            return Mono.error(new RuntimeException("未找到存储策略: " + strategyKey));
        }

        FileStorageStrategy strategy = strategyMap.get(strategyKey);
        if (strategy != null) {
            return Mono.just(strategy);
        }

        log.warn("未找到存储策略: {}", strategyKey);
        return Mono.error(new RuntimeException("未找到存储策略: " + strategyKey));
    }
    
    /**
     * 获取所有<b>可用</b>的存储策略
     *
     * <p><b>「可用」= 真的实现了</b>：占位实现（腾讯云 COS、阿里云 OSS）
     * 会被剔掉。早先这里返回全部已注册的策略，后台能选到 COS ——
     * 选中之后上传「成功」而文件根本不存在。
     *
     * @return 可用的存储策略标识列表
     */
    public List<String> getAvailableStrategies() {
        List<String> keys = strategyMap.values().stream()
                .filter(FileStorageStrategy::isImplemented)
                .map(FileStorageStrategy::getStrategyKey)
                .sorted()
                .toList();
        log.debug("获取所有可用策略: {}", keys);
        return keys;
    }
    
    /**
     * 获取策略，如果不存在则回退到默认策略
     * @param strategyKey 策略键
     * @return 存储策略
     */
    private Mono<FileStorageStrategy> getStrategyWithFallback(String strategyKey) {
        if (strategyKey != null && strategyMap.containsKey(strategyKey)) {
            return Mono.just(strategyMap.get(strategyKey));
        }
        
        // 回退到默认策略
        if (strategyMap.containsKey(DEFAULT_STRATEGY_KEY)) {
            log.warn("策略 '{}' 不存在，回退到默认策略: {}", strategyKey, DEFAULT_STRATEGY_KEY);
            return Mono.just(strategyMap.get(DEFAULT_STRATEGY_KEY));
        }
        
        log.error("没有可用的存储策略，无法提供服务");
        return Mono.error(new RuntimeException("没有可用的存储策略"));
    }
} 