package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.CommentUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.Comment;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.CommentRepository;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.service.CommentService;
import com.ryu.blog.service.impl.article.ArticleVisibilityPolicy;
import com.ryu.blog.utils.OperatorContexts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 评论服务实现类
 *
 * <p><b>三条硬规则</b>（原先都不成立）：
 * <ol>
 *   <li><b>审核状态不由客户端决定</b> —— 创建时强制「待审核」，
 *       更新内容的接口根本不接受 {@code status}。
 *       原先 Controller 直接绑定 {@code Comment} 实体，
 *       请求体里写 {@code "status": 1} 就能让评论自己变成「已通过」。</li>
 *   <li><b>改/删评论要过归属校验</b> —— 只有作者本人（或管理员）能改能删。
 *       原先任何登录用户都能改别人的评论、删别人的评论。</li>
 *   <li><b>评论列表要校验文章可见性</b> —— 公开接口
 *       {@code GET /comment/article/**} 匿名的，不校验就会把草稿文章的
 *       评论列表也读出去。</li>
 * </ol>
 *
 * <p><b>缓存只有一层</b>：原先 {@code @Cacheable} 之外还手写了一套
 * Redis get/set，两层用<b>不同的 key</b>（{@code article:comments:...} vs
 * {@code comment:article:...}）缓存同一份数据 ——
 * 写路径只驱逐其中一层，另一层继续返回旧数据。现在只留 Spring Cache 一层。
 *
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostsRepository postsRepository;

    /**
     * 审核状态：待审核
     */
    private static final int STATUS_PENDING = 0;

    /**
     * 审核状态：已通过
     */
    private static final int STATUS_APPROVED = 1;

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.COMMENT_CACHE, allEntries = true)
    public Mono<Comment> createComment(Comment comment) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            if (comment == null || comment.getPostId() == null) {
                return Mono.error(BusinessException.commentContentInvalid());
            }
            if (ctx == null || !ctx.isAuthenticated()) {
                return Mono.error(new BusinessException("未登录，无法发表评论"));
            }
            if (comment.getContent() == null || comment.getContent().isBlank()) {
                return Mono.error(BusinessException.commentContentInvalid());
            }

            // 归属人永远取当前登录用户，不信请求体里的 userId
            comment.setUserId(ctx.userId());

            // 审核状态、删除标记、主键 —— 一律由服务端决定
            comment.setStatus(STATUS_PENDING);
            comment.setIsDeleted(Boolean.FALSE);
            comment.setId(null);
            comment.setLikeCount(0);
            comment.setIsEdited(Boolean.FALSE);
            comment.setEditTime(null);
            comment.setCreateTime(LocalDateTime.now());
            comment.setUpdateTime(LocalDateTime.now());

            // 被评论的文章必须存在，且对评论者可见（不能给草稿文章评论）
            return requireArticleVisible(comment.getPostId())
                    .then(commentRepository.save(comment))
                    .doOnSuccess(saved -> log.info("评论创建成功: ID={}, 文章ID={}, 用户ID={}",
                            saved.getId(), saved.getPostId(), saved.getUserId()));
        });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.COMMENT_CACHE, allEntries = true)
    public Mono<Comment> updateComment(CommentUpdateDTO dto) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return commentRepository.findById(dto.getId())
                    .switchIfEmpty(Mono.error(BusinessException.commentNotFound()))
                    .flatMap(existing -> {
                        // 归属校验：只有作者本人（或管理员）能改
                        OwnershipPolicy.requireCanActOn(ctx, existing.getUserId());

                        // 只改内容 —— 审核状态不在这条路径上
                        existing.setContent(dto.getContent());
                        existing.setIsEdited(Boolean.TRUE);
                        existing.setEditTime(LocalDateTime.now());
                        existing.setUpdateTime(LocalDateTime.now());

                        return commentRepository.save(existing);
                    })
                    .doOnSuccess(saved -> log.info("评论更新成功: ID={}", saved.getId()));
        });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.COMMENT_CACHE,
            key = "'" + CacheConstants.COMMENT_ID_KEY + "' + #id", unless = "#result == null")
    public Mono<Comment> getCommentById(Long id) {
        return commentRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.commentNotFound()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.COMMENT_CACHE, allEntries = true)
    public Mono<Void> deleteComment(Long id) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return commentRepository.findById(id)
                    .switchIfEmpty(Mono.error(BusinessException.commentNotFound()))
                    .flatMap(comment -> {
                        // 归属校验：只有作者本人（或管理员）能删
                        OwnershipPolicy.requireCanActOn(ctx, comment.getUserId());

                        comment.setIsDeleted(Boolean.TRUE);
                        comment.setUpdateTime(LocalDateTime.now());
                        return commentRepository.save(comment).then();
                    })
                    .doOnSuccess(v -> log.info("评论删除成功: ID={}", id));
        });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.COMMENT_CACHE,
            key = "'" + CacheConstants.COMMENT_ARTICLE_KEY + "' + #articleId + ':' + #page + ':' + #size",
            unless = "#result == null")
    public Flux<Comment> getCommentsByArticleId(Long articleId, int page, int size) {
        return commentRepository.findByPostId(articleId, PageRequest.of(page, size));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.COMMENT_CACHE,
            key = "'" + CacheConstants.COMMENT_ARTICLE_COUNT_KEY + "' + #articleId",
            unless = "#result == 0")
    public Mono<Long> countCommentsByArticleId(Long articleId) {
        return commentRepository.countByPostId(articleId);
    }

    @Override
    public Mono<Void> requireArticleVisible(Long articleId) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            Long viewerId = ctx == null ? null : ctx.userId();
            return postsRepository.findById(articleId)
                    // 先把「文章不存在」和「文章不可见」都归一成同一个错误 ——
                    // 区别对待等于告诉匿名访客「这篇文章存在，只是你没权限」。
                    .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                    .flatMap(article -> {
                        boolean deleted = Boolean.TRUE.equals(article.getIsDeleted());
                        boolean visible = !deleted
                                && (ArticleVisibilityPolicy.canView(article, viewerId)
                                // 作者本人 / 管理员 —— 草稿也能评论、能看评论
                                || (article.getUserId() != null
                                && OwnershipPolicy.canActOn(ctx, article.getUserId())));
                        if (!visible) {
                            log.warn("文章不可见，拒绝访问其评论: 文章ID={}, 访问者ID={}", articleId, viewerId);
                            return Mono.error(BusinessException.postNotFound());
                        }
                        return Mono.empty();
                    });
        });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.COMMENT_CACHE,
            key = "'" + CacheConstants.COMMENT_USER_KEY + "' + #userId + ':' + #page + ':' + #size",
            unless = "#result == null")
    public Flux<Comment> getCommentsByUserId(Long userId, int page, int size) {
        return commentRepository.findByUserId(userId, PageRequest.of(page, size));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.COMMENT_CACHE,
            key = "'" + CacheConstants.COMMENT_COUNT_KEY + "' + #userId",
            unless = "#result == 0")
    public Mono<Long> countCommentsByUserId(Long userId) {
        return commentRepository.countByUserId(userId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.COMMENT_CACHE, allEntries = true)
    public Mono<Integer> updateCommentStatus(Long id, Integer status) {
        return commentRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.commentNotFound()))
                .flatMap(comment -> {
                    // 状态没变就直接返回
                    if (status != null && status.equals(comment.getStatus())) {
                        return Mono.just(0);
                    }

                    comment.setStatus(status);
                    comment.setUpdateTime(LocalDateTime.now());
                    return commentRepository.save(comment)
                            .map(saved -> {
                                log.info("评论状态更新成功: ID={}, 状态={}", id, status);
                                return 1;
                            });
                });
    }

    @Override
    public Flux<Comment> getPendingComments(int page, int size) {
        return commentRepository.findAllByStatus(STATUS_PENDING, PageRequest.of(page, size));
    }

    @Override
    public Mono<Long> countPendingComments() {
        return commentRepository.countByStatus(STATUS_PENDING);
    }
}
