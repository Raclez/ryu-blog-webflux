package com.ryu.blog.repository;

import com.ryu.blog.entity.ViewHistory;
import org.springframework.data.r2dbc.repository.Modifying;
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
     * 按匿名访客标识查询浏览历史（按时间倒序）
     *
     * @param visitorId 匿名访客标识
     * @return 浏览历史列表
     */
    Flux<ViewHistory> findByVisitorIdOrderByCreateTimeDesc(String visitorId);

    /**
     * 根据访客标识和文章ID查询浏览历史
     *
     * @param visitorId 访客标识
     * @param postId    文章ID
     * @return 浏览历史
     */
    Mono<ViewHistory> findByVisitorIdAndPostId(String visitorId, Long postId);

    /**
     * 按登录用户查询浏览历史（按时间倒序）
     *
     * @param userId 登录用户ID
     * @return 浏览历史列表
     */
    Flux<ViewHistory> findByUserIdOrderByCreateTimeDesc(Long userId);

    /**
     * 按登录用户分页查询浏览历史（按时间倒序）
     *
     * @param userId 登录用户ID
     * @param limit  每页大小
     * @param offset 偏移量
     * @return 浏览历史列表
     */
    @Query("SELECT vh.* FROM t_view_history vh WHERE vh.user_id = :userId ORDER BY vh.create_time DESC LIMIT :limit OFFSET :offset")
    Flux<ViewHistory> findUserHistoryPaged(Long userId, int limit, long offset);

    /**
     * 统计某个登录用户的浏览历史条数
     *
     * @param userId 登录用户ID
     * @return 浏览历史条数
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * 删除某个登录用户的全部浏览历史
     *
     * <p>{@code @Modifying} 不能少：没有它，Spring Data R2DBC 会按「查询」处理这条语句，
     * 试图把修改语句的结果当行集来映射 —— 影响行数丢失，方法返回空 Mono。
     *
     * @param userId 登录用户ID
     * @return 删除条数
     */
    @Modifying
    @Query("DELETE FROM t_view_history WHERE user_id = :userId")
    Mono<Integer> deleteByUserId(Long userId);

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
     * 批量获取多篇文章的浏览量
     *
     * @param articleIds 文章ID列表
     * @return 文章ID和浏览量的映射
     */
    @Query("SELECT post_id, COUNT(*) FROM t_view_history WHERE post_id IN (:articleIds) GROUP BY post_id")
    Flux<Object[]> countByArticleIds(Iterable<Long> articleIds);
} 