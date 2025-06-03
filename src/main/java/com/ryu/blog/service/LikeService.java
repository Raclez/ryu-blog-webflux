package com.ryu.blog.service;

import com.ryu.blog.entity.Like;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 点赞服务接口
 */
public interface LikeService {

    /**
     * 点赞或取消点赞
     *
     * @param type     类型：1-文章，2-评论
     * @param targetId 目标ID
     * @param userId   用户ID
     * @return 是否成功
     */
    Mono<Boolean> toggleLike(Integer type, Long targetId, Long userId);

    /**
     * 检查用户是否点赞
     *
     * @param type     类型
     * @param targetId 目标ID
     * @param userId   用户ID
     * @return 是否点赞
     */
    Mono<Boolean> checkLiked(Integer type, Long targetId, Long userId);

    /**
     * 获取点赞数
     *
     * @param type     类型
     * @param targetId 目标ID
     * @return 点赞数
     */
    Mono<Long> getLikeCount(Integer type, Long targetId);

    /**
     * 获取用户点赞列表
     *
     * @param userId 用户ID
     * @param type   类型，可为null
     * @return 点赞列表
     */
    Flux<Like> getUserLikes(Long userId, Integer type);

    /**
     * 分页获取用户点赞列表
     *
     * @param userId 用户ID
     * @param type   类型，可为null
     * @param page   页码
     * @param size   每页大小
     * @return 点赞列表和分页信息
     */
    Mono<Map<String, Object>> getUserLikesPaged(Long userId, Integer type, int page, int size);

    /**
     * 批量获取多个目标的点赞数
     *
     * @param type      类型
     * @param targetIds 目标ID列表
     * @return 目标ID和点赞数的映射
     */
    Mono<Map<Long, Long>> batchGetLikeCounts(Integer type, Iterable<Long> targetIds);
} 