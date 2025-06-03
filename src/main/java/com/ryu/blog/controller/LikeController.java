package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.Like;
import com.ryu.blog.service.LikeService;
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
 * 点赞控制器
 */
@Slf4j
//@RestController
@RequestMapping("/api/like")
@Tag(name = "点赞管理", description = "点赞相关接口")
public class LikeController {

    @Autowired
    private LikeService likeService;

    /**
     * 点赞或取消点赞
     */
    @PostMapping("/{type}/{targetId}")
    @Operation(summary = "点赞或取消点赞", description = "点赞或取消点赞")
    public Mono<Result<Boolean>> toggleLike(
            @Parameter(description = "类型：1-文章，2-评论") @PathVariable("type") Integer type,
            @Parameter(description = "目标ID") @PathVariable("targetId") Long targetId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return likeService.toggleLike(type, targetId, userId)
                .map(Result::success);
    }

    /**
     * 检查是否点赞
     */
    @GetMapping("/check/{type}/{targetId}")
    @Operation(summary = "检查是否点赞", description = "检查当前用户是否点赞")
    public Mono<Result<Boolean>> checkLiked(
            @Parameter(description = "类型：1-文章，2-评论") @PathVariable("type") Integer type,
            @Parameter(description = "目标ID") @PathVariable("targetId") Long targetId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return likeService.checkLiked(type, targetId, userId)
                .map(Result::success);
    }

    /**
     * 获取点赞数
     */
    @GetMapping("/count/{type}/{targetId}")
    @Operation(summary = "获取点赞数", description = "获取目标的点赞数")
    public Mono<Result<Long>> getLikeCount(
            @Parameter(description = "类型：1-文章，2-评论") @PathVariable("type") Integer type,
            @Parameter(description = "目标ID") @PathVariable("targetId") Long targetId) {
        return likeService.getLikeCount(type, targetId)
                .map(Result::success);
    }

    /**
     * 获取用户点赞列表
     */
    @GetMapping("/user")
    @Operation(summary = "获取用户点赞列表", description = "获取当前用户的点赞列表")
    public Mono<Result<List<Like>>> getUserLikes(
            @Parameter(description = "类型：1-文章，2-评论，不传则获取所有") @RequestParam(required = false) Integer type) {
        Long userId = StpUtil.getLoginIdAsLong();
        return likeService.getUserLikes(userId, type)
                .collectList()
                .map(Result::success);
    }

    /**
     * 分页获取用户点赞列表
     */
    @GetMapping("/user/page")
    @Operation(summary = "分页获取用户点赞列表", description = "分页获取当前用户的点赞列表")
    public Mono<Result<Map<String, Object>>> getUserLikesPaged(
            @Parameter(description = "类型：1-文章，2-评论，不传则获取所有") @RequestParam(required = false) Integer type,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return likeService.getUserLikesPaged(userId, type, page, size)
                .map(Result::success);
    }

    /**
     * 批量获取多个目标的点赞数
     */
    @PostMapping("/batch/count/{type}")
    @Operation(summary = "批量获取多个目标的点赞数", description = "批量获取多个目标的点赞数")
    public Mono<Result<Map<Long, Long>>> batchGetLikeCounts(
            @Parameter(description = "类型：1-文章，2-评论") @PathVariable("type") Integer type,
            @RequestBody List<Long> targetIds) {
        return likeService.batchGetLikeCounts(type, targetIds)
                .map(Result::success);
    }
} 