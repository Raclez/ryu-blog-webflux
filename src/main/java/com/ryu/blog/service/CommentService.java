package com.ryu.blog.service;

import com.ryu.blog.dto.CommentUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.Comment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 评论服务接口
 *
 * <p><b>鉴权模型</b>（与文章模块一致）：
 * <ul>
 *   <li><b>能力校验</b> —— Controller 上的 {@code @SaCheckPermission}；</li>
 *   <li><b>归属校验</b> —— Service 层用 {@code OwnershipPolicy}：
 *       评论只有<b>作者本人</b>（或拥有「管理他人」能力的管理员）能改、能删。</li>
 * </ul>
 *
 * <p><b>审核状态（status）不归客户端管</b>：只能通过
 * {@link #updateCommentStatus}（需要 {@code comment:approve} 能力）修改。
 *
 * @author ryu
 */
public interface CommentService {

    /**
     * 创建评论
     *
     * <p>服务端会强制：审核状态 = 待审核、删除标记 = false、ID 由数据库分配，
     * 并校验「被评论的文章存在且对评论者可见」。
     *
     * @param comment 评论信息（userId 由调用方填成当前登录用户）
     * @return 创建结果
     */
    Mono<Comment> createComment(Comment comment);

    /**
     * 更新评论
     *
     * <p>只能改<b>内容</b>；审核状态不在这条路径上。
     * 只有评论作者本人（或管理员）能改。
     *
     * @param dto 评论更新数据（id + content）
     * @return 更新结果
     */
    Mono<Comment> updateComment(CommentUpdateDTO dto);

    /**
     * 根据ID获取评论
     * @param id 评论ID
     * @return 评论信息
     */
    Mono<Comment> getCommentById(Long id);

    /**
     * 删除评论
     *
     * <p>只有评论作者本人（或管理员）能删。
     *
     * @param id 评论ID
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
     * 校验文章对当前访问者可见
     *
     * <p>公开接口 {@code GET /comment/article/**} 是匿名的 —— 如果不校验，
     * 草稿/私密文章的评论列表也会被匿名读到。
     *
     * @param articleId 文章ID
     * @return 文章可见时完成；不可见时抛业务异常
     */
    Mono<Void> requireArticleVisible(Long articleId);

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
     * 更新评论状态（审核）
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
