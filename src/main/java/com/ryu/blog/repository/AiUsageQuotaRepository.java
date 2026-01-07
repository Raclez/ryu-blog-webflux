package com.ryu.blog.repository;

import com.ryu.blog.entity.AiUsageQuota;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI使用配额存储库
 * 
 * @author ryu
 * @since 1.0
 */
@Repository
public interface AiUsageQuotaRepository extends ReactiveCrudRepository<AiUsageQuota, Long> {

    /**
     * 根据用户ID查询配额
     *
     * @param userId 用户ID
     * @return 配额信息
     */
    Mono<AiUsageQuota> findByUserId(Long userId);

    /**
     * 根据用户ID检查配额是否存在
     *
     * @param userId 用户ID
     * @return 是否存在
     */
    Mono<Boolean> existsByUserId(Long userId);

    /**
     * 根据角色ID查询配额列表
     *
     * @param roleId 角色ID
     * @return 配额列表
     */
    Flux<AiUsageQuota> findByRoleId(Long roleId);

    /**
     * 根据角色ID统计配额数量
     *
     * @param roleId 角色ID
     * @return 配额数量
     */
    Mono<Long> countByRoleId(Long roleId);

    /**
     * 查询所有配额
     *
     * @return 配额列表
     */
    Flux<AiUsageQuota> findAll();

    /**
     * 统计所有配额数量
     *
     * @return 配额数量
     */
    Mono<Long> count();
}
