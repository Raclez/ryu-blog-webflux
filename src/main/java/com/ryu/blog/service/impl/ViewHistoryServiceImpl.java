package com.ryu.blog.service.impl;

import com.ryu.blog.entity.Posts;
import com.ryu.blog.entity.ViewHistory;
import com.ryu.blog.mapper.ViewHistoryMapper;

import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.repository.ViewHistoryRepository;
import com.ryu.blog.service.ViewHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 浏览历史服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewHistoryServiceImpl implements ViewHistoryService {

    private final ViewHistoryRepository viewHistoryRepository;
    private final PostsRepository postsRepository;
    private final UserRepository userRepository;
    private final ViewHistoryMapper viewHistoryMapper;

    @Override
    @Transactional
    public Mono<Boolean> addViewHistory(Long articleId, Long userId) {
        // 先检查是否已经有浏览记录
        return viewHistoryRepository.findByVisitorIdAndPostId(userId, articleId)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        // 如果已经有记录，则更新时间
                        return viewHistoryRepository.findByVisitorIdAndPostId(userId, articleId)
                                .flatMap(history -> {
                                    history.setUpdateTime(LocalDateTime.now());
                                    return viewHistoryRepository.save(history)
                                            .flatMap(savedHistory -> updateArticleViewCount(articleId))
                                            .thenReturn(true);
                                });
                    } else {
                        // 如果没有记录，则添加新记录
                        ViewHistory history = new ViewHistory();
                        history.setPostId(articleId);
                        history.setVisitorId(userId.toString());
                        history.setViewTime(LocalDateTime.now());
                        history.setCreateTime(LocalDateTime.now());
                        history.setUpdateTime(LocalDateTime.now());
                        return viewHistoryRepository.save(history)
                                .flatMap(savedHistory -> updateArticleViewCount(articleId))
                                .thenReturn(true);
                    }
                });
    }

    /**
     * 更新文章浏览量
     */
    private Mono<Posts> updateArticleViewCount(Long articleId) {
        return postsRepository.findById(articleId)
                .flatMap(article -> {
                    Integer views = article.getViews() == null ? 0 : article.getViews();
                    article.setViews(views + 1);
                    return postsRepository.save(article);
                });
    }

    @Override
    public Flux<ViewHistory> getUserViewHistory(Long userId) {
        return viewHistoryRepository.findByVisitorIdOrderByCreateTimeDesc(userId);
    }

    @Override
    public Mono<Map<String, Object>> getUserViewHistoryPaged(Long userId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;

        final int finalPage = page;
        final int finalSize = size;

        return viewHistoryRepository.countByVisitorId(userId)
                .flatMap(total -> {
                    long offset = (finalPage - 1) * finalSize;
                    return viewHistoryRepository.findByVisitorIdOrderByCreateTimeDesc(userId, finalSize, offset)
                            .flatMap(history ->
                                    postsRepository.findById(history.getPostId())
                                    .map(article -> viewHistoryMapper.toViewHistoryVO(history, article))
                                    .defaultIfEmpty(viewHistoryMapper.toViewHistoryVO(history))
                            )
                            .collectList()
                            .map(histories -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("records", histories);
                                result.put("total", total);
                                result.put("pages", (total + finalSize - 1) / finalSize);
                                result.put("current", finalPage);
                                return result;
                            });
                });
    }

    @Override
    public Mono<Long> getArticleViewCount(Long articleId) {
        return viewHistoryRepository.countByPostId(articleId);
    }

    @Override
    public Mono<Map<Long, Long>> batchGetArticleViewCounts(Iterable<Long> articleIds) {
        return viewHistoryRepository.countByArticleIds(articleIds)
                .collectMap(
                        objects -> (Long) objects[0],  // articleId
                        objects -> (Long) objects[1]   // count
                )
                .defaultIfEmpty(Collections.emptyMap());
    }

    @Override
    @Transactional
    public Mono<Boolean> clearUserViewHistory(Long userId) {
        return viewHistoryRepository.findByVisitorIdOrderByCreateTimeDesc(userId)
                .flatMap(viewHistoryRepository::delete)
                .then(Mono.just(true))
                .onErrorReturn(false);
    }
} 