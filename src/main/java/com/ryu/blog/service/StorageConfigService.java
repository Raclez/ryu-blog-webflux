package com.ryu.blog.service;

import com.ryu.blog.dto.StorageConfigCreateDTO;
import com.ryu.blog.dto.StorageConfigQueryDTO;
import com.ryu.blog.dto.StorageConfigUpdateDTO;
import com.ryu.blog.entity.StorageConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件存储策略服务接口
 *
 * @author ryu 475118582@qq.com
 */
public interface StorageConfigService {

    /**
     * 获取所有启用的存储策略
     * @return 启用的存储策略
     */
    Mono<StorageConfig> getEnabledStrategy();

    /**
     * 根据策略的唯一标识获取存储策略
     * @param strategyKey 策略的唯一标识
     * @return 对应的存储策略
     */
    Mono<StorageConfig> getStrategyByKey(String strategyKey);

    /**
     * 创建存储策略
     * @param storageConfigCreateDTO 存储策略创建对象
     * @return 操作结果
     */
    Mono<Void> createStorageConfig(StorageConfigCreateDTO storageConfigCreateDTO);

    /**
     * 更新存储策略
     * @param storageConfigUpdateDTO 存储策略更新对象
     * @return 操作结果
     */
    Mono<Void> updateStorageConfig(StorageConfigUpdateDTO storageConfigUpdateDTO);

    /**
     * 启用或禁用存储策略
     * @param strategyKey 存储策略的唯一标识
     * @param isEnabled 是否启用
     * @return 操作结果
     */
    Mono<Void> enableOrDisableStorageConfig(String strategyKey, boolean isEnabled);

    /**
     * 分页获取存储策略信息
     * @param storageConfigQueryDTO 分页查询参数
     * @return 存储策略列表
     */
    Flux<StorageConfig> getStrategiesByPage(StorageConfigQueryDTO storageConfigQueryDTO);
    
    /**
     * 删除存储策略
     * @param id 存储策略ID
     * @return 操作结果
     */
    Mono<Void> removeById(Long id);
} 