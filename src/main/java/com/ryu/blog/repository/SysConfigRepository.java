package com.ryu.blog.repository;

import com.ryu.blog.entity.SysConfig;
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
     * @param isDeleted 是否删除
     * @return 配置信息
     */
    Mono<SysConfig> findByConfigKeyAndIsDeleted(String configKey, Integer isDeleted);

    /**
     * 根据配置分组查询配置列表
     *
     * @param configGroup 配置分组
     * @param isDeleted   是否删除
     * @return 配置列表
     */
    Flux<SysConfig> findByConfigGroupAndIsDeletedOrderByIdAsc(String configGroup, Integer isDeleted);

    /**
     * 分页查询配置列表
     *
     * @param isDeleted 是否删除
     * @param limit     限制数量
     * @param offset    偏移量
     * @return 配置列表
     */
    @Query("SELECT * FROM t_sys_config WHERE is_deleted = :isDeleted ORDER BY config_group, id LIMIT :limit OFFSET :offset")
    Flux<SysConfig> findByIsDeletedOrderByConfigGroupAndIdAsc(Integer isDeleted, int limit, long offset);

    /**
     * 统计配置数量
     *
     * @param isDeleted 是否删除
     * @return 配置数量
     */
    Mono<Long> countByIsDeleted(Integer isDeleted);

    /**
     * 根据配置键模糊查询配置列表
     *
     * @param configKey 配置键
     * @param isDeleted 是否删除
     * @return 配置列表
     */
    @Query("SELECT * FROM t_sys_config WHERE config_key LIKE CONCAT('%', :configKey, '%') AND is_deleted = :isDeleted ORDER BY config_group, id")
    Flux<SysConfig> findByConfigKeyLikeAndIsDeleted(String configKey, Integer isDeleted);

    /**
     * 根据配置分组统计配置数量
     *
     * @param configGroup 配置分组
     * @param isDeleted   是否删除
     * @return 配置数量
     */
    Mono<Long> countByConfigGroupAndIsDeleted(String configGroup, Integer isDeleted);
} 