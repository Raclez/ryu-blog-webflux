package com.ryu.blog.service.impl;

import com.ryu.blog.entity.Comment;
import com.ryu.blog.entity.User;
import com.ryu.blog.mapper.CommentMapper;
import com.ryu.blog.repository.CommentRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.CommentService;
import com.ryu.blog.vo.CommentTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final CommentMapper commentMapper;
    
    private static final String ARTICLE_COMMENTS_CACHE_KEY = "article:comments:";
    private static final String USER_COMMENTS_CACHE_KEY = "user:comments:";
    private static final String COMMENT_COUNT_CACHE_KEY = "comment:count:";

    @Override
    @Transactional
    public Mono<Comment> createComment(Comment comment) {
        // 设置默认值
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        comment.setIsDeleted(0);
        
        // 默认状态为待审核
        if (comment.getStatus() == null) {
            comment.setStatus(0);
        }
        
        return commentRepository.save(comment)
                .flatMap(savedComment -> {
                    // 如果是已通过状态，则增加文章评论数
                    if (savedComment.getStatus() == 1) {
                        return commentRepository.incrementPostComments(savedComment.getPostId())
                                .thenReturn(savedComment);
                    }
                    return Mono.just(savedComment);
                })
                .doOnSuccess(savedComment -> {
                    // 清除缓存
                    clearCommentCache(savedComment.getPostId(), savedComment.getUserId());
                });
    }

    @Override
    @Transactional
    public Mono<Comment> updateComment(Comment comment) {
        return commentRepository.findById(comment.getId())
                .switchIfEmpty(Mono.error(new RuntimeException("评论不存在")))
                .flatMap(existingComment -> {
                    // 更新基本信息
                    if (comment.getContent() != null) {
                        existingComment.setContent(comment.getContent());
                    }
                    if (comment.getStatus() != null) {
                        // 如果状态发生变化，需要更新文章评论数
                        if (existingComment.getStatus() != comment.getStatus()) {
                            if (comment.getStatus() == 1 && existingComment.getStatus() != 1) {
                                // 从非通过状态变为通过状态，增加文章评论数
                                return commentRepository.incrementPostComments(existingComment.getPostId())
                                        .then(updateCommentFields(existingComment, comment));
                            } else if (comment.getStatus() != 1 && existingComment.getStatus() == 1) {
                                // 从通过状态变为非通过状态，减少文章评论数
                                return commentRepository.decrementPostComments(existingComment.getPostId())
                                        .then(updateCommentFields(existingComment, comment));
                            }
                        }
                    }
                    return updateCommentFields(existingComment, comment);
                });
    }

    private Mono<Comment> updateCommentFields(Comment existingComment, Comment comment) {
        // 更新基本信息
        if (comment.getContent() != null) {
            existingComment.setContent(comment.getContent());
        }
        if (comment.getStatus() != null) {
            existingComment.setStatus(comment.getStatus());
        }
        
        existingComment.setUpdateTime(LocalDateTime.now());
        
        return commentRepository.save(existingComment)
                .doOnSuccess(savedComment -> {
                    // 清除缓存
                    clearCommentCache(savedComment.getPostId(), savedComment.getUserId());
                });
    }

    @Override
    public Mono<Comment> getCommentById(Long id) {
        return commentRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("评论不存在")));
    }

    @Override
    @Transactional
    public Mono<Void> deleteComment(Long id) {
        return commentRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("评论不存在")))
                .flatMap(comment -> {
                    // 逻辑删除
                    comment.setIsDeleted(1);
                    comment.setUpdateTime(LocalDateTime.now());
                    return commentRepository.save(comment)
                            .flatMap(savedComment -> {
                                // 如果是已通过状态，则减少文章评论数
                                if (savedComment.getStatus() == 1) {
                                    return commentRepository.decrementPostComments(savedComment.getPostId())
                                            .then();
                                }
                                return Mono.empty();
                            })
                            .doOnSuccess(v -> {
                                // 清除缓存
                                clearCommentCache(comment.getPostId(), comment.getUserId());
                            });
                });
    }

    @Override
    public Flux<Comment> getCommentsByArticleId(Long articleId, int page, int size) {
        // 先尝试从缓存中获取
        String key = ARTICLE_COMMENTS_CACHE_KEY + articleId + ":" + page + ":" + size;
        return reactiveRedisTemplate.opsForValue().get(key)
                .cast(Comment[].class)
                .flatMapMany(comments -> Flux.fromArray(comments))
                .switchIfEmpty(
                        // 查询顶级评论
                        commentRepository.findByPostId(articleId, PageRequest.of(page, size))
                                .collectList()
                                .flatMap(comments -> {
                                    if (!comments.isEmpty()) {
                                        // 更新缓存
                                        return reactiveRedisTemplate.opsForValue().set(key, comments.toArray(), Duration.ofMinutes(10))
                                                .thenReturn(comments);
                                    }
                                    return Mono.just(comments);
                                })
                                .flatMapIterable(comments -> comments)
                );
    }

    @Override
    public Mono<Long> countCommentsByArticleId(Long articleId) {
        // 先尝试从缓存中获取
        String key = COMMENT_COUNT_CACHE_KEY + "article:" + articleId;
        return reactiveRedisTemplate.opsForValue().get(key)
                .cast(Long.class)
                .switchIfEmpty(
                        commentRepository.countByPostId(articleId)
                                .flatMap(count -> {
                                    // 更新缓存
                                    return reactiveRedisTemplate.opsForValue().set(key, count, Duration.ofMinutes(10))
                                            .thenReturn(count);
                                })
                );
    }

    @Override
    public Flux<Comment> getCommentsByUserId(Long userId, int page, int size) {
        // 先尝试从缓存中获取
        String key = USER_COMMENTS_CACHE_KEY + userId + ":" + page + ":" + size;
        return reactiveRedisTemplate.opsForValue().get(key)
                .cast(Comment[].class)
                .flatMapMany(comments -> Flux.fromArray(comments))
                .switchIfEmpty(
                        commentRepository.findByUserId(userId, PageRequest.of(page, size))
                                .collectList()
                                .flatMap(comments -> {
                                    if (!comments.isEmpty()) {
                                        // 更新缓存
                                        return reactiveRedisTemplate.opsForValue().set(key, comments.toArray(), Duration.ofMinutes(10))
                                                .thenReturn(comments);
                                    }
                                    return Mono.just(comments);
                                })
                                .flatMapIterable(comments -> comments)
                );
    }

    @Override
    public Mono<Long> countCommentsByUserId(Long userId) {
        // 先尝试从缓存中获取
        String key = COMMENT_COUNT_CACHE_KEY + "user:" + userId;
        return reactiveRedisTemplate.opsForValue().get(key)
                .cast(Long.class)
                .switchIfEmpty(
                        commentRepository.countByUserId(userId)
                                .flatMap(count -> {
                                    // 更新缓存
                                    return reactiveRedisTemplate.opsForValue().set(key, count, Duration.ofMinutes(10))
                                            .thenReturn(count);
                                })
                );
    }

    @Override
    @Transactional
    public Mono<Integer> updateCommentStatus(Long id, Integer status) {
        return commentRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("评论不存在")))
                .flatMap(comment -> {
                    // 如果状态没有变化，直接返回
                    if (comment.getStatus().equals(status)) {
                        return Mono.just(0);
                    }
                    
                    // 更新状态
                    return commentRepository.updateStatus(id, status)
                            .flatMap(result -> {
                                // 如果状态变为已通过，则增加文章评论数
                                if (status == 1 && comment.getStatus() != 1) {
                                    return commentRepository.incrementPostComments(comment.getPostId());
                                }
                                // 如果状态从已通过变为其他状态，则减少文章评论数
                                else if (status != 1 && comment.getStatus() == 1) {
                                    return commentRepository.decrementPostComments(comment.getPostId());
                                }
                                return Mono.just(result);
                            })
                            .doOnSuccess(v -> {
                                // 清除缓存
                                clearCommentCache(comment.getPostId(), comment.getUserId());
                            });
                });
    }

    @Override
    public Flux<Comment> getPendingComments(int page, int size) {
        return commentRepository.findAllByStatus(0, PageRequest.of(page, size));
    }

    @Override
    public Mono<Long> countPendingComments() {
        return commentRepository.countByStatus(0);
    }
    
    /**
     * 清除评论相关缓存
     * @param articleId 文章ID
     * @param userId 用户ID
     */
    private void clearCommentCache(Long articleId, Long userId) {
        // 清除文章评论缓存
        String articleKey = ARTICLE_COMMENTS_CACHE_KEY + articleId + ":*";
        reactiveRedisTemplate.keys(articleKey).flatMap(reactiveRedisTemplate::delete).subscribe();
        
        // 清除用户评论缓存
        String userKey = USER_COMMENTS_CACHE_KEY + userId + ":*";
        reactiveRedisTemplate.keys(userKey).flatMap(reactiveRedisTemplate::delete).subscribe();
        
        // 清除评论计数缓存
        String articleCountKey = COMMENT_COUNT_CACHE_KEY + "article:" + articleId;
        String userCountKey = COMMENT_COUNT_CACHE_KEY + "user:" + userId;
        reactiveRedisTemplate.delete(articleCountKey).subscribe();
        reactiveRedisTemplate.delete(userCountKey).subscribe();
    }
} 