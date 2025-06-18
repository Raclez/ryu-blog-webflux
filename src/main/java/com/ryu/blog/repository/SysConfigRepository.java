package com.ryu.blog.repository;

import com.ryu.blog.entity.SysConfig;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 系统配置存储库
 */
@Repository
public interface SysConfigRepository extends ReactiveCrudRepository<SysConfig, Long> {

    /**
     * 根据配置键查询配置
     *
     * @param configKey 配置键
     * @return 配置信息
     */
    Mono<SysConfig> findByConfigKey(String configKey);

    /**
     * 根据配置键查询配置（包含逻辑删除条件）
     *
     * @param configKey 配置键
     * @param isDeleted 是否删除
     * @return 配置信息
     */
    Mono<SysConfig> findByConfigKeyAndIsDeleted(String configKey, Integer isDeleted);

    /**
     * 根据配置键检查配置是否存在
     *
     * @param configKey 配置键
     * @return 是否存在
     */
    Mono<Boolean> existsByConfigKey(String configKey);

    /**
     * 根据配置键前缀查询配置列表
     *
     * @param prefix 配置键前缀
     * @return 配置列表
     */
    Flux<SysConfig> findByConfigKeyStartingWith(String prefix);

    /**
     * 根据配置键前缀分页查询配置列表
     *
     * @param prefix 配置键前缀
     * @param pageable 分页参数
     * @return 配置列表
     */
    Flux<SysConfig> findByConfigKeyStartingWith(String prefix, Pageable pageable);

    /**
     * 根据配置键前缀统计配置数量
     *
     * @param prefix 配置键前缀
     * @return 配置数量
     */
    Mono<Long> countByConfigKeyStartingWith(String prefix);

    /**
     * 根据用户ID查询配置列表
     *
     * @param userId 用户ID
     * @return 配置列表
     */
    Flux<SysConfig> findByUserId(Long userId);

    /**
     * 根据用户ID分页查询配置列表
     *
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 配置列表
     */
    Flux<SysConfig> findByUserId(Long userId, Pageable pageable);

    /**
     * 分页查询所有配置
     *
     * @param pageable 分页参数
     * @return 配置列表
     */
    Flux<SysConfig> findAllBy(Pageable pageable);

    /**
     * 统计配置数量
     *
     * @param isDeleted 是否删除
     * @return 配置数量
     */
    Mono<Long> countByIsDeleted(Integer isDeleted);

    /**
     * 根据配置键包含关系查询配置列表
     *
     * @param configKey 配置键关键字
     * @return 配置列表
     */
    Flux<SysConfig> findByConfigKeyContaining(String configKey);

    /**
     * 根据配置键包含关系分页查询配置列表
     *
     * @param configKey 配置键关键字
     * @param pageable 分页参数
     * @return 配置列表
     */
    Flux<SysConfig> findByConfigKeyContaining(String configKey, Pageable pageable);

    /**
     * 根据配置键查询配置列表
     *
     * @param configKey 配置键关键字
     * @return 配置列表
     */
    Flux<SysConfig> findByConfigKeyContainingOrRemarkContaining(String configKey, String remark);

    /**
     * 根据配置键包含关系统计配置数量
     *
     * @param configKey 配置键关键字
     * @return 配置数量
     */
    Mono<Long> countByConfigKeyContaining(String configKey);

    /**
     * 根据用户ID和配置键查询配置
     *
     * @param userId 用户ID
     * @param configKey 配置键
     * @return 配置信息
     */
    Mono<SysConfig> findByUserIdAndConfigKey(Long userId, String configKey);

    /**
     * 根据状态查询配置列表
     *
     * @param status 状态
     * @return 配置列表
     */
    Flux<SysConfig> findByStatus(Boolean status);
} 