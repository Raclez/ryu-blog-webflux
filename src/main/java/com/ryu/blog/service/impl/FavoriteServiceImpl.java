package com.ryu.blog.service.impl;

import com.ryu.blog.entity.Favorite;
import com.ryu.blog.repository.FavoriteRepository;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final PostsRepository postsRepository;
    private final UserRepository userRepository;

    /**
     * 切换收藏状态
     * 如果文章已被收藏，则取消收藏；如果未收藏，则添加收藏
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return true表示添加收藏，false表示取消收藏
     */
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

    /**
     * 检查用户是否已收藏指定文章
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return true表示已收藏，false表示未收藏
     */
    @Override
    public Mono<Boolean> checkFavorited(Long articleId, Long userId) {
        return favoriteRepository.findByUserIdAndArticleIdAndIsDeleted(userId, articleId, 0)
                .map(favorite -> true)
                .defaultIfEmpty(false);
    }

    /**
     * 获取文章的收藏数量
     *
     * @param articleId 文章ID
     * @return 收藏数量
     */
    @Override
    public Mono<Long> getFavoriteCount(Long articleId) {
        return favoriteRepository.countByArticleIdAndIsDeleted(articleId, 0);
    }

    /**
     * 获取用户的所有收藏（包含文章和用户信息）
     *
     * @param userId 用户ID
     * @return 收藏列表
     */
    @Override
    public Flux<Favorite> getUserFavorites(Long userId) {
        return favoriteRepository.findByUserIdAndIsDeletedOrderByCreateTimeDesc(userId, 0)
                .flatMap(this::enrichFavorite);
    }

    /**
     * 丰富收藏信息
     * 关联查询文章和用户信息
     *
     * @param favorite 收藏记录
     * @return 包含完整信息的收藏记录
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

    /**
     * 分页获取用户的收藏列表
     *
     * @param userId 用户ID
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果，包含records（收藏列表）、total（总数）、pages（总页数）、current（当前页）
     */
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

    /**
     * 批量获取文章的收藏数量
     *
     * @param articleIds 文章ID集合
     * @return Map，key为文章ID，value为收藏数量
     */
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