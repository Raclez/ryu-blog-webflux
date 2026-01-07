package com.ryu.blog.repository;

import com.ryu.blog.entity.AiGenerationHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * AI生成历史存储库
 * 
 * @author ryu
 * @since 1.0
 */
@Repository
public interface AiGenerationHistoryRepository extends ReactiveCrudRepository<AiGenerationHistory, Long> {

    /**
     * 根据用户ID查询生成历史（按创建时间倒序）
     *
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 生成历史列表
     */
    Flux<AiGenerationHistory> findByUserIdAndIsDeletedOrderByCreateTimeDesc(Long userId, Integer isDeleted, Pageable pageable);

    /**
     * 根据用户ID统计生成历史数量
     *
     * @param userId 用户ID
     * @param isDeleted 是否删除
     * @return 历史数量
     */
    Mono<Long> countByUserIdAndIsDeleted(Long userId, Integer isDeleted);

    /**
     * 根据提供商名称查询生成历史
     *
     * @param providerName 提供商名称
     * @param isDeleted 是否删除
     * @param pageable 分页参数
     * @return 生成历史列表
     */
    Flux<AiGenerationHistory> findByProviderNameAndIsDeletedOrderByCreateTimeDesc(String providerName, Integer isDeleted, Pageable pageable);

    /**
     * 根据用户ID和提供商名称查询生成历史
     *
     * @param userId 用户ID
     * @param providerName 提供商名称
     * @param isDeleted 是否删除
     * @param pageable 分页参数
     * @return 生成历史列表
     */
    Flux<AiGenerationHistory> findByUserIdAndProviderNameAndIsDeletedOrderByCreateTimeDesc(
            Long userId, String providerName, Integer isDeleted, Pageable pageable);

    /**
     * 根据用户ID统计总令牌使用量
     *
     * @param userId 用户ID
     * @param isDeleted 是否删除
     * @return 总令牌数
     */
    @Query("SELECT COALESCE(SUM(token_count), 0) FROM t_ai_generation_history WHERE user_id = :userId AND is_deleted = :isDeleted")
    Mono<Long> sumTokenCountByUserIdAndIsDeleted(Long userId, Integer isDeleted);

    /**
     * 根据用户ID统计总成本
     *
     * @param userId 用户ID
     * @param isDeleted 是否删除
     * @return 总成本
     */
    @Query("SELECT COALESCE(SUM(estimated_cost), 0.0) FROM t_ai_generation_history WHERE user_id = :userId AND is_deleted = :isDeleted")
    Mono<Double> sumCostByUserIdAndIsDeleted(Long userId, Integer isDeleted);

    /**
     * 查询所有生成历史（按创建时间倒序）
     *
     * @param isDeleted 是否删除
     * @param pageable 分页参数
     * @return 生成历史列表
     */
    Flux<AiGenerationHistory> findByIsDeletedOrderByCreateTimeDesc(Integer isDeleted, Pageable pageable);

    /**
     * 统计所有生成历史数量
     *
     * @param isDeleted 是否删除
     * @return 历史数量
     */
    Mono<Long> countByIsDeleted(Integer isDeleted);

    /**
     * 根据用户ID和时间范围查询生成历史
     *
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param isDeleted 是否删除
     * @return 生成历史列表
     */
    Flux<AiGenerationHistory> findByUserIdAndCreateTimeBetween(Long userId, LocalDateTime startTime, LocalDateTime endTime, Integer isDeleted);
}
