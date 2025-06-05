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
     * 获取配置属性（异步方法）
     * 从策略配置中获取指定属性的值，如果不存在则返回默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值的Mono
     */
    Mono<String> getConfigPropertyAsync(String key, String defaultValue);
    
    /**
     * 获取配置属性（同步方法）
     * 从策略配置中获取指定属性的值，如果不存在则返回默认值
     * 
     * 注意：这是一个兼容性方法，新代码应该使用 getConfigPropertyAsync 方法
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值
     * @deprecated 请使用 getConfigPropertyAsync 方法代替
     */
//    @Deprecated
//    default String getConfigProperty(String key, String defaultValue) {
//        // 默认实现：阻塞等待异步方法的结果
//        // 这不是一个好的实践，但为了兼容性而提供
//        return getConfigPropertyAsync(key, defaultValue).block();
//    }
} 