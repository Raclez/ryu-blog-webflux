package com.ryu.blog.service;

import com.ryu.blog.dto.ViewHistoryDTO;
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
     * 添加浏览历史记录
     * 同一用户在短时间内多次访问同一文章只会记录一次浏览量
     *
     * @param viewHistoryDTO 浏览历史数据传输对象
     * @return 是否添加成功
     */
    Mono<Boolean> addViewHistory(ViewHistoryDTO viewHistoryDTO);

    /**
     * 获取用户浏览历史
     *
     * @param userId 用户ID
     * @return 浏览历史列表
     */
    Flux<ViewHistory> getUserViewHistory(Long userId);

    /**
     * 分页获取用户浏览历史
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
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
     * 批量获取文章浏览量
     *
     * @param articleIds 文章ID列表
     * @return 文章ID到浏览量的映射
     */
    Mono<Map<Long, Long>> batchGetArticleViewCounts(Iterable<Long> articleIds);

    /**
     * 清空用户浏览历史
     *
     * @param userId 用户ID
     * @return 是否清空成功
     */
    Mono<Boolean> clearUserViewHistory(Long userId);

    /**
     * 获取浏览历史统计信息
     *
     * @return 统计信息
     */
    Mono<ViewHistoryStatsVO> getViewHistoryStats();

    /**
     * 获取文章当前浏览量（优先从Redis获取，Redis无数据则从数据库获取）
     * 
     * @param articleId 文章ID
     * @return 浏览量
     */
    Mono<Integer> getArticleCurrentViews(Long articleId);

    /**
     * 批量同步Redis中的文章浏览量到数据库
     * 用于定时任务调用，确保数据库中的浏览量与Redis保持同步
     * 
     * @return 同步的文章数量
     */
    Mono<Integer> syncViewCountsToDatabase();
} 