package com.ryu.blog.service.impl;

import com.ryu.blog.dto.StorageConfigCreateDTO;
import com.ryu.blog.dto.StorageConfigQueryDTO;
import com.ryu.blog.dto.StorageConfigUpdateDTO;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.event.ConfigChangeEvent;
import com.ryu.blog.mapper.StorageConfigMapper;
import com.ryu.blog.repository.StorageConfigRepository;
import com.ryu.blog.service.StorageConfigService;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 存储策略服务实现类
 *
 * @author ryu 475118582@qq.com
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StorageConfigServiceImpl implements StorageConfigService {

    private final StorageConfigRepository storageConfigRepository;
    private final StorageConfigMapper storageConfigMapper;
    private final StorageStrategyRegistry strategyRegistry;
    private final StorageConfigManager configManager;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Mono<StorageConfig> getEnabledStrategy() {
        log.debug("查询已启用存储策略");
        return storageConfigRepository.findOneByIsEnableAndIsDeleted(true, 0)
                .doOnNext(config -> log.info("获取到已启用的存储策略: {}, config: {}", config.getStrategyName(), config.getConfig()))
                .doOnError(e -> log.error("查询已启用存储策略出错", e))
                .doOnSuccess(config -> {
                    if (config == null) {
                        log.warn("未找到已启用的存储策略");
                    } else {
                        log.info("最终获取到已启用的存储策略: {}", config.getStrategyName());
                    }
                });
    }

    @Override
    public Mono<StorageConfig> getStrategyByKey(String strategyKey) {
        log.debug("查询存储策略: {}", strategyKey);
        return configManager.getStrategyConfig(strategyKey)
                .doOnNext(config -> log.debug("获取到存储策略: {}", config.getStrategyName()));
    }

    @Override
    @Transactional
    public Mono<Void> createStorageConfig(StorageConfigCreateDTO dto) {
        log.info("创建存储策略: {}", dto.getStrategyName());
        
        // 如果设置为启用，则先禁用其他策略
        Mono<Void> disableOthers = Mono.just(true)
                .filter(__ -> Boolean.TRUE.equals(dto.getIsEnable()))
                .flatMap(__ -> disableAllStrategies())
                .then();
        
        return disableOthers
                .then(Mono.fromCallable(() -> storageConfigMapper.toEntity(dto)))
                .flatMap(storageConfigRepository::save)
                .doOnNext(savedConfig -> {
                    log.info("存储策略创建成功: {}", savedConfig.getStrategyName());
                    // 发布配置变更事件
                    publishConfigChangeEvent(savedConfig.getStrategyKey());
                })
                .then(configManager.reloadConfig());
    }

    @Override
    @Transactional
    public Mono<Void> updateStorageConfig(StorageConfigUpdateDTO dto) {
        log.info("更新存储策略: {}", dto.getStrategyName());
        
        // 如果设置为启用，则先禁用其他策略
        Mono<Void> disableOthers = Mono.just(true)
                .filter(__ -> Boolean.TRUE.equals(dto.getIsEnable()))
                .flatMap(__ -> disableAllStrategies())
                .then();
        
        return disableOthers
                .then(storageConfigRepository.findById(dto.getId()))
                .switchIfEmpty(Mono.error(new RuntimeException("存储策略不存在: " + dto.getId())))
                .flatMap(existingConfig -> {
                    StorageConfig updatedConfig = storageConfigMapper.toEntity(dto);
                    updatedConfig.setCreateTime(existingConfig.getCreateTime());
                    updatedConfig.setIsDeleted(existingConfig.getIsDeleted());
                    return storageConfigRepository.save(updatedConfig);
                })
                .doOnNext(savedConfig -> {
                    log.info("存储策略更新成功: {}", savedConfig.getStrategyName());
                    // 发布配置变更事件
                    publishConfigChangeEvent(savedConfig.getStrategyKey());
                })
                .then(configManager.reloadConfig());
    }

    @Override
    @Transactional
    public Mono<Void> enableOrDisableStorageConfig(String strategyKey, boolean isEnabled) {
        log.info("{} 存储策略: {}", isEnabled ? "启用" : "停用", strategyKey);
        
        // 如果要启用策略，先禁用所有策略
        Mono<Void> disableOthers = Mono.just(true)
                .filter(__ -> isEnabled)
                .flatMap(__ -> disableAllStrategies())
                .then();
        
        return disableOthers
                .then(storageConfigRepository.findByStrategyKeyAndIsDeleted(strategyKey, 0))
                .switchIfEmpty(Mono.error(new RuntimeException("存储策略不存在: " + strategyKey)))
                .flatMap(config -> {
                    config.setIsEnable(isEnabled);
                    config.setUpdateTime(LocalDateTime.now());
                    return storageConfigRepository.save(config);
                })
                .doOnNext(config -> {
                    log.info("存储策略状态已更新: {}, isEnabled={}", config.getStrategyName(), isEnabled);
                    // 发布配置变更事件
                    publishConfigChangeEvent(strategyKey);
                })
                .then(configManager.reloadConfig());
    }

    @Override
    public Flux<StorageConfig> getStrategiesByPage(StorageConfigQueryDTO queryDTO) {
        log.debug("分页查询存储策略, 页码: {}, 每页数量: {}", queryDTO.getCurrentPage(), queryDTO.getPageSize());
        
        // 构建条件查询
        return storageConfigRepository.findAllByIsDeleted(0)
                .filter(config -> {
                    // 按名称过滤
                    if (queryDTO.getStrategyName() != null && !queryDTO.getStrategyName().isEmpty()) {
                        return config.getStrategyName().contains(queryDTO.getStrategyName());
                    }
                    return true;
                })
                // 分页处理 (TODO: 实际项目中可以使用更高效的分页查询)
                .skip((long) (queryDTO.getCurrentPage() - 1) * queryDTO.getPageSize())
                .take(queryDTO.getPageSize());
    }

    @Override
    @Transactional
    public Mono<Void> removeById(Long id) {
        log.info("删除存储策略: {}", id);
        return storageConfigRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("存储策略不存在: " + id)))
                .flatMap(config -> {
                    // 逻辑删除
                    config.setIsDeleted(1);
                    config.setUpdateTime(LocalDateTime.now());
                    return storageConfigRepository.save(config)
                            .doOnNext(savedConfig -> {
                                log.info("存储策略已删除: {}", savedConfig.getStrategyName());
                                // 发布配置变更事件
                                publishConfigChangeEvent(savedConfig.getStrategyKey());
                            });
                })
                .then(configManager.reloadConfig());
    }
    
    /**
     * 禁用所有存储策略
     * @return 操作结果
     */
    private Mono<Void> disableAllStrategies() {
        return storageConfigRepository.findAllByIsDeleted(0)
                .filter(config -> Boolean.TRUE.equals(config.getIsEnable()))
                .flatMap(config -> {
                    config.setIsEnable(false);
                    config.setUpdateTime(LocalDateTime.now());
                    return storageConfigRepository.save(config);
                })
                .then();
    }
    
    /**
     * 发布配置变更事件
     * @param strategyKey 策略键
     */
    private void publishConfigChangeEvent(String strategyKey) {
        configManager.publishConfigChangeEvent(strategyKey);
        log.debug("已发布存储配置变更事件: strategyKey={}", strategyKey);
    }
} 