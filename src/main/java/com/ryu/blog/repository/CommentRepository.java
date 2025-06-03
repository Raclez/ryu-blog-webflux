package com.ryu.blog.repository;

import com.ryu.blog.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 评论存储库接口
 * @author ryu
 */
@Repository
public interface CommentRepository extends R2dbcRepository<Comment, Long> {

    /**
     * 根据文章ID查询评论（只查询顶级评论）
     * @param postId 文章ID
     * @param pageable 分页参数
     * @return 评论列表
     */
    @Query("SELECT * FROM t_comments WHERE post_id = :postId AND parent_comment_id IS NULL AND status = 1 AND is_deleted = 0 ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Comment> findByPostId(Long postId, Pageable pageable);

    /**
     * 根据文章ID统计评论数量（只统计顶级评论）
     * @param postId 文章ID
     * @return 评论数量
     */
    @Query("SELECT COUNT(*) FROM t_comments WHERE post_id = :postId AND parent_comment_id IS NULL AND status = 1 AND is_deleted = 0")
    Mono<Long> countByPostId(Long postId);

    /**
     * 根据父评论ID查询子评论
     * @param parentId 父评论ID
     * @return 子评论列表
     */
    @Query("SELECT * FROM t_comments WHERE parent_comment_id = :parentId AND status = 1 AND is_deleted = 0 ORDER BY create_time ASC")
    Flux<Comment> findByParentId(Long parentId);

    /**
     * 根据用户ID查询评论
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 评论列表
     */
    @Query("SELECT * FROM t_comments WHERE user_id = :userId AND status = 1 AND is_deleted = 0 ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Comment> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据用户ID统计评论数量
     * @param userId 用户ID
     * @return 评论数量
     */
    @Query("SELECT COUNT(*) FROM t_comments WHERE user_id = :userId AND status = 1 AND is_deleted = 0")
    Mono<Long> countByUserId(Long userId);

    /**
     * 更新评论状态
     * @param id 评论ID
     * @param status 状态
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_comments SET status = :status, update_time = NOW() WHERE id = :id")
    Mono<Integer> updateStatus(Long id, Integer status);

    /**
     * 增加文章评论数
     * @param postId 文章ID
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_posts SET comments = comments + 1 WHERE id = :postId")
    Mono<Integer> incrementPostComments(Long postId);

    /**
     * 减少文章评论数
     * @param postId 文章ID
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_posts SET comments = comments - 1 WHERE id = :postId")
    Mono<Integer> decrementPostComments(Long postId);

    /**
     * 查询所有评论（用于管理员审核）
     * @param status 状态
     * @param pageable 分页参数
     * @return 评论列表
     */
    @Query("SELECT * FROM t_comments WHERE status = :status AND is_deleted = 0 ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Comment> findAllByStatus(Integer status, Pageable pageable);

    /**
     * 统计评论数量（用于管理员审核）
     * @param status 状态
     * @return 评论数量
     */
    @Query("SELECT COUNT(*) FROM t_comments WHERE status = :status AND is_deleted = 0")
    Mono<Long> countByStatus(Integer status);
} 