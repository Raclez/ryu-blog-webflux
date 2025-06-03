package com.ryu.blog.service;

import com.ryu.blog.entity.Comment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 评论服务接口
 * @author ryu
 */
public interface CommentService {

    /**
     * 创建评论
     * @param comment 评论信息
     * @return 创建结果
     */
    Mono<Comment> createComment(Comment comment);

    /**
     * 更新评论
     * @param comment 评论信息
     * @return 更新结果
     */
    Mono<Comment> updateComment(Comment comment);

    /**
     * 根据ID获取评论
     * @param id 评论ID
     * @return 评论信息
     */
    Mono<Comment> getCommentById(Long id);

    /**
     * 删除评论
     * @param id 评论ID
     * @return 删除结果
     */
    Mono<Void> deleteComment(Long id);

    /**
     * 根据文章ID获取评论列表
     * @param articleId 文章ID
     * @param page 页码
     * @param size 每页大小
     * @return 评论列表
     */
    Flux<Comment> getCommentsByArticleId(Long articleId, int page, int size);

    /**
     * 根据文章ID获取评论总数
     * @param articleId 文章ID
     * @return 评论总数
     */
    Mono<Long> countCommentsByArticleId(Long articleId);

    /**
     * 根据用户ID获取评论列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 评论列表
     */
    Flux<Comment> getCommentsByUserId(Long userId, int page, int size);

    /**
     * 根据用户ID获取评论总数
     * @param userId 用户ID
     * @return 评论总数
     */
    Mono<Long> countCommentsByUserId(Long userId);

    /**
     * 更新评论状态
     * @param id 评论ID
     * @param status 状态
     * @return 更新结果
     */
    Mono<Integer> updateCommentStatus(Long id, Integer status);

    /**
     * 获取待审核的评论列表
     * @param page 页码
     * @param size 每页大小
     * @return 评论列表
     */
    Flux<Comment> getPendingComments(int page, int size);

    /**
     * 获取待审核的评论总数
     * @return 评论总数
     */
    Mono<Long> countPendingComments();
} 