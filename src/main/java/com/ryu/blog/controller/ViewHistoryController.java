package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.ViewHistory;
import com.ryu.blog.service.ViewHistoryService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.ViewHistoryStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 浏览历史控制器
 */
@Slf4j
@RestController
@RequestMapping("/view")
@Tag(name = "浏览历史管理", description = "浏览历史相关接口")
public class ViewHistoryController {

    @Autowired
    private ViewHistoryService viewHistoryService;

    /**
     * 添加浏览记录
     */
    @PostMapping("/record/{articleId}")
    @Operation(summary = "添加浏览记录", description = "添加文章浏览记录")
    public Mono<Result<Boolean>> addViewHistory(
            @Parameter(description = "文章ID") @PathVariable("articleId") Long articleId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return viewHistoryService.addViewHistory(articleId, userId)
                .map(Result::success);
    }

    /**
     * 获取用户浏览历史
     */
    @GetMapping("/user")
    @Operation(summary = "获取用户浏览历史", description = "获取当前用户的浏览历史")
    public Mono<Result<List<ViewHistory>>> getUserViewHistory() {
        Long userId = StpUtil.getLoginIdAsLong();
        return viewHistoryService.getUserViewHistory(userId)
                .collectList()
                .map(Result::success);
    }

    /**
     * 分页获取浏览历史
     */
    @GetMapping("/page")
    @Operation(summary = "分页获取浏览历史", description = "分页获取浏览历史")
    public Mono<Result<Map<String, Object>>> getUserViewHistoryPaged(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int currentPage,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return viewHistoryService.getUserViewHistoryPaged(currentPage, pageSize)
                .map(Result::success);
    }

    /**
     * 获取文章浏览量
     */
    @GetMapping("/count/{articleId}")
    @Operation(summary = "获取文章浏览量", description = "获取文章的浏览量")
    public Mono<Result<Long>> getArticleViewCount(
            @Parameter(description = "文章ID") @PathVariable("articleId") Long articleId) {
        return viewHistoryService.getArticleViewCount(articleId)
                .map(Result::success);
    }

    /**
     * 批量获取多篇文章的浏览量
     */
    @PostMapping("/batch/count")
    @Operation(summary = "批量获取多篇文章的浏览量", description = "批量获取多篇文章的浏览量")
    public Mono<Result<Map<Long, Long>>> batchGetArticleViewCounts(@RequestBody List<Long> articleIds) {
        return viewHistoryService.batchGetArticleViewCounts(articleIds)
                .map(Result::success);
    }

    /**
     * 清空用户浏览历史
     */
    @DeleteMapping("/clear")
    @Operation(summary = "清空用户浏览历史", description = "清空当前用户的浏览历史")
    public Mono<Result<Boolean>> clearUserViewHistory() {
        Long userId = StpUtil.getLoginIdAsLong();
        return viewHistoryService.clearUserViewHistory(userId)
                .map(Result::success);
    }

    /**
     * 获取浏览历史统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取浏览历史统计信息", description = "获取浏览历史的统计数据，包括PV、UV等信息")
    public Mono<Result<ViewHistoryStatsVO>> getViewHistoryStats() {
        return viewHistoryService.getViewHistoryStats()
                .map(Result::success);
    }
} 