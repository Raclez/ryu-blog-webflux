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
    Mono<StorageConfig> findByStrategyKeyAndIsDeleted(String strategyKey, Boolean isDeleted);

    /**
     * 查询所有未删除的配置
     *
     * @param isDeleted 是否删除
     * @return 存储配置列表
     */
    Flux<StorageConfig> findAllByIsDeleted(Boolean isDeleted);

    /**
     * 查询启用的配置
     *
     * @param isEnable 是否启用
     * @param isDeleted 是否删除
     * @return 存储配置列表
     */
    Flux<StorageConfig> findByIsEnableAndIsDeleted(Boolean isEnable, Boolean isDeleted);
    
    /**
     * 查询单个启用的配置
     *
     * @param isEnable 是否启用
     * @param isDeleted 是否删除
     * @return 存储配置
     */
    @Query("SELECT * FROM t_storage_config WHERE is_enable = :isEnable AND is_deleted = :isDeleted LIMIT 1")
    Mono<StorageConfig> findOneByIsEnableAndIsDeleted(Boolean isEnable, Boolean isDeleted);

    /**
     * 分页查询未删除的存储配置。
     *
     * <p>分页在<b>数据库</b>里做：早先服务层是 {@code findAllByIsDeleted(false)}
     * 把整表拉进内存再 {@code skip/take}，配置一多就白读一堆行。
     *
     * @param isDeleted 是否删除
     * @param limit     每页数量
     * @param offset    偏移量
     * @return 当前页的配置
     */
    @Query("SELECT * FROM t_storage_config WHERE is_deleted = :isDeleted "
            + "ORDER BY id DESC LIMIT :limit OFFSET :offset")
    Flux<StorageConfig> findPageByIsDeleted(Boolean isDeleted, long limit, long offset);

    /**
     * 按策略名称模糊分页查询未删除的存储配置。
     *
     * @param isDeleted 是否删除
     * @param name      策略名称片段
     * @param limit     每页数量
     * @param offset    偏移量
     * @return 当前页的配置
     */
    @Query("SELECT * FROM t_storage_config WHERE is_deleted = :isDeleted "
            + "AND strategy_name LIKE CONCAT('%', :name, '%') "
            + "ORDER BY id DESC LIMIT :limit OFFSET :offset")
    Flux<StorageConfig> findPageByNameAndIsDeleted(Boolean isDeleted, String name, long limit, long offset);
} 