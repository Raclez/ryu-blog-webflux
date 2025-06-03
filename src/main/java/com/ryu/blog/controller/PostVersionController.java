package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.PostVersion;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.service.ArticleVersionService;
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
 * 文章版本控制器
 */
@Slf4j
@RestController
@RequestMapping("/postVersion")
@Tag(name = "文章版本管理", description = "文章版本相关接口")
public class PostVersionController {

    @Autowired
    private ArticleVersionService articleVersionService;

    /**
     * 获取文章版本列表
     */
    @GetMapping("/list/{postId}")
    @Operation(summary = "获取文章版本列表", description = "获取指定文章的版本列表")
    public Mono<Result<List<PostVersion>>> getVersions(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId) {
        return articleVersionService.getVersions(postId)
                .collectList()
                .map(Result::success);
    }

    /**
     * 分页获取文章版本列表
     */
    @GetMapping("/page/{postId}")
    @Operation(summary = "分页获取文章版本列表", description = "分页获取指定文章的版本列表")
    public Mono<Result<Map<String, Object>>> getVersionsPaged(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        return articleVersionService.getVersionsPaged(postId, page, size)
                .map(Result::success);
    }

    /**
     * 获取文章指定版本
     */
    @GetMapping("/{postId}/{version}")
    @Operation(summary = "获取文章指定版本", description = "获取文章的指定版本")
    public Mono<Result<PostVersion>> getVersion(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId,
            @Parameter(description = "版本号") @PathVariable("version") Integer version) {
        return articleVersionService.getVersion(postId, version)
                .map(Result::success)
                .defaultIfEmpty(Result.fail("版本不存在"));
    }

    /**
     * 获取文章最新版本
     */
    @GetMapping("/latest/{postId}")
    @Operation(summary = "获取文章最新版本", description = "获取文章的最新版本")
    public Mono<Result<PostVersion>> getLatestVersion(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId) {
        return articleVersionService.getLatestVersion(postId)
                .map(Result::success)
                .defaultIfEmpty(Result.fail("版本不存在"));
    }

    /**
     * 回滚到指定版本
     */
    @PostMapping("/rollback/{postId}/{version}")
    @Operation(summary = "回滚到指定版本", description = "将文章回滚到指定版本")
    public Mono<Result<Posts>> rollbackToVersion(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId,
            @Parameter(description = "版本号") @PathVariable("version") Integer version) {
        Long userId = StpUtil.getLoginIdAsLong();
        return articleVersionService.rollbackToVersion(postId, version, userId)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.fail(e.getMessage())));
    }

    /**
     * 删除文章版本
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除文章版本", description = "删除指定的文章版本")
    public Mono<Result<Boolean>> deleteVersion(
            @Parameter(description = "版本ID") @PathVariable("id") Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return articleVersionService.deleteVersion(id, userId)
                .map(result -> result ? Result.success(true) : Result.fail("删除失败，可能无权限或不允许删除最新版本"));
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/compare/{postId}/{version1}/{version2}")
    @Operation(summary = "比较版本差异", description = "比较文章两个版本的差异")
    public Mono<Result<Map<String, Object>>> compareVersions(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId,
            @Parameter(description = "版本1") @PathVariable("version1") Integer version1,
            @Parameter(description = "版本2") @PathVariable("version2") Integer version2) {
        return articleVersionService.compareVersions(postId, version1, version2)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.fail(e.getMessage())));
    }
} 