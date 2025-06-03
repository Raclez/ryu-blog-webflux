package com.ryu.blog.service;

import com.ryu.blog.entity.Favorite;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 收藏服务接口
 */
public interface FavoriteService {

    /**
     * 添加或取消收藏
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return 是否成功
     */
    Mono<Boolean> toggleFavorite(Long articleId, Long userId);

    /**
     * 检查用户是否收藏了文章
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return 是否收藏
     */
    Mono<Boolean> checkFavorited(Long articleId, Long userId);

    /**
     * 获取文章收藏数
     *
     * @param articleId 文章ID
     * @return 收藏数
     */
    Mono<Long> getFavoriteCount(Long articleId);

    /**
     * 获取用户收藏列表
     *
     * @param userId 用户ID
     * @return 收藏列表
     */
    Flux<Favorite> getUserFavorites(Long userId);

    /**
     * 分页获取用户收藏列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 收藏列表和分页信息
     */
    Mono<Map<String, Object>> getUserFavoritesPaged(Long userId, int page, int size);

    /**
     * 批量获取多篇文章的收藏数
     *
     * @param articleIds 文章ID列表
     * @return 文章ID和收藏数的映射
     */
    Mono<Map<Long, Long>> batchGetFavoriteCounts(Iterable<Long> articleIds);
} 