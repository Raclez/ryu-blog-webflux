package com.ryu.blog.service;

import com.ryu.blog.entity.ViewHistory;
import com.ryu.blog.vo.ViewHistoryStatsVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 浏览历史服务接口
 */
public interface ViewHistoryService {

    /**
     * 添加浏览记录
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return 是否成功
     */
    Mono<Boolean> addViewHistory(Long articleId, Long userId);

    /**
     * 获取用户浏览历史
     *
     * @param userId 用户ID
     * @return 浏览历史列表
     */
    Flux<ViewHistory> getUserViewHistory(Long userId);

    /**
     * 分页获取浏览历史
     *
     * @param page   页码
     * @param size   每页大小
     * @return 浏览历史列表和分页信息
     */
    Mono<Map<String, Object>> getUserViewHistoryPaged(int page, int size);

    /**
     * 获取文章浏览量
     *
     * @param articleId 文章ID
     * @return 浏览量
     */
    Mono<Long> getArticleViewCount(Long articleId);

    /**
     * 批量获取多篇文章的浏览量
     *
     * @param articleIds 文章ID列表
     * @return 文章ID和浏览量的映射
     */
    Mono<Map<Long, Long>> batchGetArticleViewCounts(Iterable<Long> articleIds);

    /**
     * 清空用户浏览历史
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    Mono<Boolean> clearUserViewHistory(Long userId);
    
    /**
     * 获取浏览历史统计信息
     *
     * @return 浏览历史统计信息
     */
    Mono<ViewHistoryStatsVO> getViewHistoryStats();
} 