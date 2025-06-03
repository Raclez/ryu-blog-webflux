package com.ryu.blog.repository;

import com.ryu.blog.entity.ViewHistory;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 浏览历史存储库
 */
@Repository
public interface ViewHistoryRepository extends R2dbcRepository<ViewHistory, Long> {

    /**
     * 根据用户ID查询浏览历史
     *
     * @param visitorId 用户ID
     * @return 浏览历史列表
     */
    Flux<ViewHistory> findByVisitorIdOrderByCreateTimeDesc(Long visitorId);

    /**
     * 根据用户ID和文章ID查询浏览历史
     *
     * @param VisitorId    用户ID
     * @param postId 文章ID
     * @return 浏览历史
     */
    Mono<ViewHistory> findByVisitorIdAndPostId(Long VisitorId, Long postId);

    /**
     * 根据文章ID查询浏览历史
     *
     * @param articleId 文章ID
     * @return 浏览历史列表
     */
    Flux<ViewHistory> findByPostId(Long articleId);

    /**
     * 统计文章浏览量
     *
     * @param postId 文章ID
     * @return 浏览量
     */
    Mono<Long> countByPostId(Long postId);

    /**
     * 分页查询用户浏览历史
     *
     * @param VisitorId 用户ID
     * @param limit  限制数量
     * @param offset 偏移量
     * @return 浏览历史列表
     */
    @Query("SELECT vh.* FROM t_view_history vh WHERE vh.user_id = :userId ORDER BY vh.create_time DESC LIMIT :limit OFFSET :offset")
    Flux<ViewHistory> findByVisitorIdOrderByCreateTimeDesc(Long VisitorId, int limit, long offset);

    /**
     * 统计用户浏览历史数量
     *
     * @param VisitorId 用户ID
     * @return 浏览历史数量
     */
    Mono<Long> countByVisitorId(Long VisitorId);

    /**
     * 批量获取多篇文章的浏览量
     *
     * @param articleIds 文章ID列表
     * @return 文章ID和浏览量的映射
     */
    @Query("SELECT article_id, COUNT(*) as count FROM t_view_history WHERE article_id IN (:articleIds) GROUP BY article_id")
    Flux<Object[]> countByArticleIds(Iterable<Long> articleIds);
} 