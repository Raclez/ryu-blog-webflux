package com.ryu.blog.repository;

import com.ryu.blog.entity.StorageConfig;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 存储配置仓库接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface StorageConfigRepository extends R2dbcRepository<StorageConfig, Long> {

    /**
     * 根据策略键查询配置
     *
     * @param strategyKey 策略键
     * @param isDeleted 是否删除
     * @return 存储配置
     */
    Mono<StorageConfig> findByStrategyKeyAndIsDeleted(String strategyKey, Integer isDeleted);

    /**
     * 查询所有未删除的配置
     *
     * @param isDeleted 是否删除
     * @return 存储配置列表
     */
    Flux<StorageConfig> findAllByIsDeleted(Integer isDeleted);

    /**
     * 查询启用的配置
     *
     * @param isEnable 是否启用
     * @param isDeleted 是否删除
     * @return 存储配置列表
     */
    Flux<StorageConfig> findByIsEnableAndIsDeleted(Boolean isEnable, Integer isDeleted);
    
    /**
     * 查询单个启用的配置
     *
     * @param isEnable 是否启用
     * @param isDeleted 是否删除
     * @return 存储配置
     */
    @Query("SELECT * FROM t_storage_config WHERE is_enable = :isEnable AND is_deleted = :isDeleted LIMIT 1")
    Mono<StorageConfig> findOneByIsEnableAndIsDeleted(Boolean isEnable, Integer isDeleted);
} 