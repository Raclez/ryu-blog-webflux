package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.Favorite;
import com.ryu.blog.service.FavoriteService;
import com.ryu.blog.utils.Result;
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
 * 收藏控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/favorite")
@Tag(name = "收藏管理", description = "收藏相关接口")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    /**
     * 添加或取消收藏
     */
    @PostMapping("/{articleId}")
    @Operation(summary = "添加或取消收藏", description = "添加或取消收藏")
    public Mono<Result<Boolean>> toggleFavorite(
            @Parameter(description = "文章ID") @PathVariable("articleId") Long articleId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return favoriteService.toggleFavorite(articleId, userId)
                .map(Result::success);
    }

    /**
     * 检查是否收藏
     */
    @GetMapping("/check/{articleId}")
    @Operation(summary = "检查是否收藏", description = "检查当前用户是否收藏了文章")
    public Mono<Result<Boolean>> checkFavorited(
            @Parameter(description = "文章ID") @PathVariable("articleId") Long articleId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return favoriteService.checkFavorited(articleId, userId)
                .map(Result::success);
    }

    /**
     * 获取收藏数
     */
    @GetMapping("/count/{articleId}")
    @Operation(summary = "获取收藏数", description = "获取文章的收藏数")
    public Mono<Result<Long>> getFavoriteCount(
            @Parameter(description = "文章ID") @PathVariable("articleId") Long articleId) {
        return favoriteService.getFavoriteCount(articleId)
                .map(Result::success);
    }

    /**
     * 获取用户收藏列表
     */
    @GetMapping("/user")
    @Operation(summary = "获取用户收藏列表", description = "获取当前用户的收藏列表")
    public Mono<Result<List<Favorite>>> getUserFavorites() {
        Long userId = StpUtil.getLoginIdAsLong();
        return favoriteService.getUserFavorites(userId)
                .collectList()
                .map(Result::success);
    }

    /**
     * 分页获取用户收藏列表
     */
    @GetMapping("/user/page")
    @Operation(summary = "分页获取用户收藏列表", description = "分页获取当前用户的收藏列表")
    public Mono<Result<Map<String, Object>>> getUserFavoritesPaged(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return favoriteService.getUserFavoritesPaged(userId, page, size)
                .map(Result::success);
    }

    /**
     * 批量获取多篇文章的收藏数
     */
    @PostMapping("/batch/count")
    @Operation(summary = "批量获取多篇文章的收藏数", description = "批量获取多篇文章的收藏数")
    public Mono<Result<Map<Long, Long>>> batchGetFavoriteCounts(@RequestBody List<Long> articleIds) {
        return favoriteService.batchGetFavoriteCounts(articleIds)
                .map(Result::success);
    }
}