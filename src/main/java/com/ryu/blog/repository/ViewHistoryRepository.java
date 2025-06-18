package com.ryu.blog.repository;

import com.ryu.blog.entity.ViewHistory;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
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
     * @param visitorId    用户ID
     * @param postId 文章ID
     * @return 浏览历史
     */
    Mono<ViewHistory> findByVisitorIdAndPostId(Long visitorId, Long postId);

    /**
     * 根据文章ID查询浏览历史
     *
     * @param articleId 文章ID
     * @return 浏览历史列表
     */
    Flux<ViewHistory> findByPostId(Long articleId);

    /**
     * 根据文章ID统计浏览量
     *
     * @param postId 文章ID
     * @return 浏览量
     */
    @Query("SELECT COUNT(*) FROM t_view_history WHERE post_id = :postId")
    Mono<Long> countByPostId(Long postId);

    /**
     * 分页查询用户浏览历史
     *
     * @param visitorId 用户ID
     * @param limit  限制数量
     * @param offset 偏移量
     * @return 浏览历史列表
     */
    @Query("SELECT vh.* FROM t_view_history vh WHERE vh.user_id = :userId ORDER BY vh.create_time DESC LIMIT :limit OFFSET :offset")
    Flux<ViewHistory> findByVisitorIdOrderByCreateTimeDesc(Long visitorId, int limit, long offset);

    /**
     * 分页查询所有浏览历史，按创建时间倒序排序
     *
     * @param limit  每页大小
     * @param offset 偏移量
     * @return 浏览历史列表
     */
    @Query("SELECT * FROM t_view_history ORDER BY create_time DESC LIMIT :limit OFFSET :offset")
    Flux<ViewHistory> findOrderByCreateTimeDesc(int limit, long offset);

    /**
     * 统计用户浏览历史数量
     *
     * @param visitorId 用户ID
     * @return 浏览历史数量
     */
    Mono<Long> countByVisitorId(Long visitorId);

    /**
     * 批量获取多篇文章的浏览量
     *
     * @param articleIds 文章ID列表
     * @return 文章ID和浏览量的映射
     */
    @Query("SELECT post_id, COUNT(*) FROM t_view_history WHERE post_id IN (:articleIds) GROUP BY post_id")
    Flux<Object[]> countByArticleIds(Iterable<Long> articleIds);
} 