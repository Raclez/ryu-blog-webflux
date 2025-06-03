package com.ryu.blog.repository;

import com.ryu.blog.entity.Favorite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 收藏存储库
 */
@Repository
public interface FavoriteRepository extends R2dbcRepository<Favorite, Long> {

    /**
     * 根据用户ID和文章ID查询收藏记录
     *
     * @param userId    用户ID
     * @param articleId 文章ID
     * @return 收藏记录
     */
    Mono<Favorite> findByUserIdAndArticleIdAndIsDeleted(Long userId, Long articleId, Integer isDeleted);

    /**
     * 根据文章ID查询收藏记录
     *
     * @param articleId 文章ID
     * @return 收藏记录列表
     */
    Flux<Favorite> findByArticleIdAndIsDeleted(Long articleId, Integer isDeleted);

    /**
     * 统计某篇文章的收藏数
     *
     * @param articleId 文章ID
     * @return 收藏数
     */
    Mono<Long> countByArticleIdAndIsDeleted(Long articleId, Integer isDeleted);

    /**
     * 根据用户ID查询收藏记录
     *
     * @param userId 用户ID
     * @return 收藏记录列表
     */
    Flux<Favorite> findByUserIdAndIsDeletedOrderByCreateTimeDesc(Long userId, Integer isDeleted);

    /**
     * 分页查询用户收藏
     *
     * @param userId    用户ID
     * @param isDeleted 是否删除
     * @param pageable  分页参数
     * @return 收藏记录列表
     */
    @Query("SELECT f.* FROM t_favorite f WHERE f.user_id = :userId AND f.is_deleted = :isDeleted ORDER BY f.create_time DESC LIMIT :limit OFFSET :offset")
    Flux<Favorite> findByUserIdAndIsDeletedOrderByCreateTimeDesc(Long userId, Integer isDeleted, int limit, long offset);

    /**
     * 统计用户收藏数量
     *
     * @param userId    用户ID
     * @param isDeleted 是否删除
     * @return 收藏数量
     */
    Mono<Long> countByUserIdAndIsDeleted(Long userId, Integer isDeleted);

    /**
     * 批量获取多篇文章的收藏数
     *
     * @param articleIds 文章ID列表
     * @return 文章ID和收藏数的映射
     */
    @Query("SELECT article_id, COUNT(*) as count FROM t_favorite WHERE article_id IN (:articleIds) AND is_deleted = 0 GROUP BY article_id")
    Flux<Object[]> countByArticleIdsAndIsDeleted(Iterable<Long> articleIds);
} 