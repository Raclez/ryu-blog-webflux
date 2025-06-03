package com.ryu.blog.service.impl;

import com.ryu.blog.entity.Favorite;
import com.ryu.blog.repository.FavoriteRepository;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.FavoriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 收藏服务实现类
 */
@Slf4j
@Service
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public Mono<Boolean> toggleFavorite(Long articleId, Long userId) {
        // 先检查收藏记录是否存在
        return favoriteRepository.findByUserIdAndArticleIdAndIsDeleted(userId, articleId, 0)
                .flatMap(favorite -> {
                    // 如果存在，则取消收藏
                    favorite.setIsDeleted(1);
                    favorite.setUpdateTime(LocalDateTime.now());
                    return favoriteRepository.save(favorite).map(f -> false);
                })
                .switchIfEmpty(
                        // 如果不存在，则添加收藏
                        Mono.defer(() -> {
                            Favorite favorite = new Favorite();
                            favorite.setPostId(articleId);
                            favorite.setUserId(userId);
                            favorite.setCreateTime(LocalDateTime.now());
                            favorite.setUpdateTime(LocalDateTime.now());
                            favorite.setIsDeleted(0);
                            return favoriteRepository.save(favorite).map(f -> true);
                        })
                );
    }

    @Override
    public Mono<Boolean> checkFavorited(Long articleId, Long userId) {
        return favoriteRepository.findByUserIdAndArticleIdAndIsDeleted(userId, articleId, 0)
                .map(favorite -> true)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> getFavoriteCount(Long articleId) {
        return favoriteRepository.countByArticleIdAndIsDeleted(articleId, 0);
    }

    @Override
    public Flux<Favorite> getUserFavorites(Long userId) {
        return favoriteRepository.findByUserIdAndIsDeletedOrderByCreateTimeDesc(userId, 0)
                .flatMap(this::enrichFavorite);
    }

    /**
     * 丰富收藏信息
     */
    private Mono<Favorite> enrichFavorite(Favorite favorite) {
        return postsRepository.findById(favorite.getPostId())
                .doOnNext(favorite::setArticle)
                .thenReturn(favorite)
                .flatMap(f -> userRepository.findById(f.getUserId())
                        .doOnNext(f::setUser)
                        .thenReturn(f)
                );
    }

    @Override
    public Mono<Map<String, Object>> getUserFavoritesPaged(Long userId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;

        int finalPage = page;
        int finalSize = size;

        return favoriteRepository.countByUserIdAndIsDeleted(userId, 0)
                .flatMap(total -> {
                    long offset = (finalPage - 1) * finalSize;
                    return favoriteRepository.findByUserIdAndIsDeletedOrderByCreateTimeDesc(userId, 0, finalSize, offset)
                            .flatMap(this::enrichFavorite)
                            .collectList()
                            .map(favorites -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("records", favorites);
                                result.put("total", total);
                                result.put("pages", (total + finalSize - 1) / finalSize);
                                result.put("current", finalPage);
                                return result;
                            });
                });
    }

    @Override
    public Mono<Map<Long, Long>> batchGetFavoriteCounts(Iterable<Long> articleIds) {
        return favoriteRepository.countByArticleIdsAndIsDeleted(articleIds)
                .collectMap(
                        objects -> (Long) objects[0],  // articleId
                        objects -> (Long) objects[1]   // count
                )
                .defaultIfEmpty(Collections.emptyMap());
    }
} 