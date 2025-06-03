package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.Comment;
import com.ryu.blog.service.CommentService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 评论控制器
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/api/content/comment")
@RequiredArgsConstructor
@Tag(name = "评论管理", description = "评论相关接口")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "创建评论", description = "创建新评论")
    @PostMapping
    public Mono<Result<Comment>> createComment(@RequestBody @Validated Comment comment) {
        // 设置评论用户ID为当前登录用户
        comment.setUserId(StpUtil.getLoginIdAsLong());
        return commentService.createComment(comment)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "更新评论", description = "更新已有评论")
    @PutMapping("/{id}")
    public Mono<Result<Comment>> updateComment(@PathVariable Long id, @RequestBody Comment comment) {
        comment.setId(id);
        return commentService.updateComment(comment)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "获取评论详情", description = "根据ID获取评论详情")
    @GetMapping("/{id}")
    public Mono<Result<Comment>> getCommentById(@PathVariable Long id) {
        return commentService.getCommentById(id)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "删除评论", description = "根据ID删除评论")
    @DeleteMapping("/{id}")
    public Mono<Result<String>> deleteComment(@PathVariable Long id) {
        return commentService.deleteComment(id)
                .thenReturn(Result.success("删除成功"))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "获取文章评论列表", description = "分页获取文章评论列表")
    @GetMapping("/article/{articleId}")
    public Mono<Result<Map<String, Object>>> getCommentsByArticleId(
            @PathVariable Long articleId,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        return commentService.countCommentsByArticleId(articleId)
                .flatMap(total -> {
                    return commentService.getCommentsByArticleId(articleId, page, size)
                            .collectList()
                            .map(comments -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("total", total);
                                result.put("comments", comments);
                                return Result.success(result);
                            });
                })
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "获取用户评论列表", description = "分页获取用户评论列表")
    @GetMapping("/user/{userId}")
    public Mono<Result<Map<String, Object>>> getCommentsByUserId(
            @PathVariable Long userId,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        return commentService.countCommentsByUserId(userId)
                .flatMap(total -> {
                    return commentService.getCommentsByUserId(userId, page, size)
                            .collectList()
                            .map(comments -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("total", total);
                                result.put("comments", comments);
                                return Result.success(result);
                            });
                })
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "获取我的评论列表", description = "分页获取当前登录用户的评论列表")
    @GetMapping("/my")
    public Mono<Result<Map<String, Object>>> getMyComments(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return getCommentsByUserId(userId, page, size);
    }

    @Operation(summary = "更新评论状态", description = "更新评论状态（管理员）")
    @PutMapping("/{id}/status")
    public Mono<Result<String>> updateCommentStatus(
            @PathVariable Long id,
            @Parameter(description = "状态：0-待审核，1-已通过，2-已拒绝") @RequestParam Integer status) {
        return commentService.updateCommentStatus(id, status)
                .map(result -> Result.success("更新成功"))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "获取待审核评论列表", description = "分页获取待审核评论列表（管理员）")
    @GetMapping("/pending")
    public Mono<Result<Map<String, Object>>> getPendingComments(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        return commentService.countPendingComments()
                .flatMap(total -> {
                    return commentService.getPendingComments(page, size)
                            .collectList()
                            .map(comments -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("total", total);
                                result.put("comments", comments);
                                return Result.success(result);
                            });
                })
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
} 