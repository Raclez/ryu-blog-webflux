package com.ryu.blog.strategy;

import com.ryu.blog.entity.StorageConfig;
import reactor.core.publisher.Mono;

/**
 * 可配置的存储策略接口
 * 扩展基本存储策略接口，增加配置相关方法
 *
 * @author ryu 475118582@qq.com
 */
public interface ConfigurableStorageStrategy extends FileStorageStrategy {
    
    /**
     * 配置策略
     * 根据提供的配置信息初始化或更新策略
     *
     * @param config 存储配置
     * @return 配置结果
     */
    Mono<Void> configure(StorageConfig config);
    
    /**
     * 获取配置属性
     * 从策略配置中获取指定属性的值，如果不存在则返回默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值
     */
    String getConfigProperty(String key, String defaultValue);
} 