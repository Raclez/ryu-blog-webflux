package com.ryu.blog.strategy;

import com.ryu.blog.entity.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * 存储策略工厂，作为对外统一的接口
 * 协调StorageConfigManager和StorageStrategyRegistry
 *
 * @author ryu 475118582@qq.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StorageStrategyFactory {
    
    private final StorageConfigManager configManager;
    private final StorageStrategyRegistry strategyRegistry;
    
    @PostConstruct
    public void init() {
        log.info("初始化存储策略工厂...");
    }
    
    /**
     * 获取当前活跃的存储策略
     * @return 存储策略
     */
    public Mono<FileStorageStrategy> getActiveStrategy() {
        // 委托给StorageStrategyRegistry
        return strategyRegistry.getActiveStrategy();
    }
    
    /**
     * 获取指定的存储策略
     * @param strategyKey 策略标识
     * @return 存储策略
     */
    public Mono<FileStorageStrategy> getStrategy(String strategyKey) {
        // 委托给StorageStrategyRegistry
        return strategyRegistry.getStrategy(strategyKey);
    }
    
    /**
     * 从数据库重新加载配置
     * @return 完成信号
     */
    public Mono<Void> reloadConfig() {
        log.debug("开始重新加载存储配置");
        return configManager.reloadConfig();
    }
    
    /**
     * 获取所有可用的存储策略
     * @return 存储策略列表
     */
    public List<String> getAvailableStrategies() {
        return strategyRegistry.getAvailableStrategies();
    }
    
    /**
     * 获取策略配置
     * @param strategyKey 策略键
     * @return 策略配置
     */
    public Mono<StorageConfig> getStrategyConfig(String strategyKey) {
        return configManager.getStrategyConfig(strategyKey);
    }
    
    /**
     * 清除配置缓存
     */
    public void clearConfigCache() {
        configManager.clearConfigCache();
        log.info("存储配置缓存已清除");
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        return configManager.getCacheStats();
    }
    
    /**
     * 设置当前活跃的策略键
     * @param strategyKey 策略键
     */
    public void setActiveStrategyKey(String strategyKey) {
        configManager.setActiveStrategyKey(strategyKey);
    }
    
    /**
     * 获取当前活跃的策略键
     * @return 策略键
     */
    public String getActiveStrategyKey() {
        return configManager.getActiveStrategyKey();
    }
} 