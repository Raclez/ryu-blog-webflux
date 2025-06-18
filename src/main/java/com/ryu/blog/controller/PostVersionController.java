package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.PostVersion;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.service.ArticleVersionService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 文章版本控制器
 * 负责处理文章版本相关的HTTP请求，包括版本列表查询、版本详情、版本回滚和版本比较等功能
 */
@Slf4j
@RestController
@RequestMapping("/postVersion")
@RequiredArgsConstructor
@Tag(name = "文章版本管理", description = "文章版本相关接口")
public class PostVersionController {

    private final ArticleVersionService articleVersionService;

    /**
     * 获取文章版本列表
     * 
     * @param postId 文章ID
     * @return 版本列表
     */
    @GetMapping("/{postId}/versions")
    @Operation(summary = "获取文章版本列表", description = "获取指定文章的全部版本列表")
    public Mono<Result<List<PostVersion>>> getVersions(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId) {
        log.info("获取文章版本列表: 文章ID={}", postId);
        
        return articleVersionService.getVersions(postId)
                .collectList()
                .map(versions -> {
                    log.info("获取文章版本列表成功: 文章ID={}, 版本数量={}", postId, versions.size());
                    return Result.success(versions);
                })
                .onErrorResume(e -> {
                    log.error("获取文章版本列表失败: 文章ID={}, 错误信息={}", postId, e.getMessage(), e);
                    return Mono.just(Result.fail("获取版本列表失败: " + e.getMessage()));
                });
    }


    /**
     * 获取文章指定版本
     * 
     * @param postId 文章ID
     * @param version 版本号
     * @return 版本详情
     */
    @GetMapping("/detail/{postId}/versions/{version}")
    @Operation(summary = "获取文章指定版本", description = "根据文章ID和版本号获取指定版本的详细信息")
    public Mono<Result<PostVersion>> getVersion(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId,
            @Parameter(description = "版本号") @PathVariable("version") Integer version) {
        log.info("获取文章指定版本: 文章ID={}, 版本号={}", postId, version);
        
        return articleVersionService.getVersion(postId, version)
                .map(postVersion -> {
                    log.info("获取文章指定版本成功: 文章ID={}, 版本号={}", postId, version);
                    return Result.success(postVersion);
                })
                .onErrorResume(e -> {
                    log.error("获取文章指定版本失败: 文章ID={}, 版本号={}, 错误信息={}",
                            postId, version, e.getMessage(), e);
                    return Mono.just(Result.fail("获取版本详情失败: " + e.getMessage()));
                })
                .defaultIfEmpty(Result.fail("版本不存在"));
    }

    /**
     * 获取文章最新版本
     * 
     * @param postId 文章ID
     * @return 最新版本详情
     */
    @GetMapping("/latest/{postId}")
    @Operation(summary = "获取文章最新版本", description = "获取指定文章的最新版本信息")
    public Mono<Result<PostVersion>> getLatestVersion(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId) {
        log.info("获取文章最新版本: 文章ID={}", postId);
        
        return articleVersionService.getLatestVersion(postId)
                .map(postVersion -> {
                    log.info("获取文章最新版本成功: 文章ID={}, 版本号={}", postId, postVersion.getVersion());
                    return Result.success(postVersion);
                })
                .onErrorResume(e -> {
                    log.error("获取文章最新版本失败: 文章ID={}, 错误信息={}", postId, e.getMessage(), e);
                    return Mono.just(Result.fail("获取最新版本失败: " + e.getMessage()));
                })
                .defaultIfEmpty(Result.fail("文章没有版本记录"));
    }

    /**
     * 回滚到指定版本
     * 
     * @param postId 文章ID
     * @param version 版本号
     * @return 回滚后的文章
     */
    @PostMapping("/rollback/{postId}/{version}")
    @Operation(summary = "回滚到指定版本", description = "将文章内容回滚到指定的历史版本，需要文章所有者权限")
    public Mono<Result<Posts>> rollbackToVersion(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId,
            @Parameter(description = "版本号") @PathVariable("version") Integer version) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("回滚文章到指定版本: 文章ID={}, 版本号={}, 用户ID={}", postId, version, userId);
        
        return articleVersionService.rollbackToVersion(postId, version, userId)
                .map(posts -> {
                    log.info("回滚文章到指定版本成功: 文章ID={}, 版本号={}", postId, version);
                    return Result.success(posts);
                })
                .onErrorResume(e -> {
                    log.error("回滚文章到指定版本失败: 文章ID={}, 版本号={}, 错误信息={}",
                            postId, version, e.getMessage(), e);
                    return Mono.just(Result.fail("回滚失败: " + e.getMessage()));
                });
    }

    /**
     * 删除文章版本
     * 
     * @param id 版本ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除文章版本", description = "删除指定的文章版本，需要文章所有者权限，最新版本不可删除")
    public Mono<Result<Boolean>> deleteVersion(
            @Parameter(description = "版本ID") @PathVariable("id") Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("删除文章版本: 版本ID={}, 用户ID={}", id, userId);
        
        return articleVersionService.deleteVersion(id, userId)
                .map(result -> {
                    if (result) {
                        log.info("删除文章版本成功: 版本ID={}", id);
                        return Result.success(true);
                    } else {
                        log.warn("删除文章版本失败: 版本ID={}, 可能是权限不足或是最新版本", id);
                        return Result.<Boolean>fail("删除失败，可能无权限或不允许删除最新版本");
                    }
                })
                .onErrorResume(e -> {
                    log.error("删除文章版本发生错误: 版本ID={}, 错误信息={}", id, e.getMessage(), e);
                    return Mono.just(Result.<Boolean>fail("删除失败: " + e.getMessage()));
                });
    }

    /**
     * 比较两个版本的差异
     * 
     * @param postId 文章ID
     * @param version1 版本1
     * @param version2 版本2
     * @return 差异信息
     */
    @GetMapping("/compare/{postId}/{version1}/{version2}")
    @Operation(summary = "比较版本差异", description = "比较同一篇文章的两个不同版本之间的内容差异")
    public Mono<Result<Map<String, Object>>> compareVersions(
            @Parameter(description = "文章ID") @PathVariable("postId") Long postId,
            @Parameter(description = "版本1") @PathVariable("version1") Integer version1,
            @Parameter(description = "版本2") @PathVariable("version2") Integer version2) {
        log.info("比较文章版本差异: 文章ID={}, 版本1={}, 版本2={}", postId, version1, version2);
        
        return articleVersionService.compareVersions(postId, version1, version2)
                .map(diff -> {
                    log.info("比较文章版本差异成功: 文章ID={}, 版本1={}, 版本2={}", postId, version1, version2);
                    return Result.success(diff);
                })
                .onErrorResume(e -> {
                    log.error("比较文章版本差异失败: 文章ID={}, 版本1={}, 版本2={}, 错误信息={}",
                            postId, version1, version2, e.getMessage(), e);
                    return Mono.just(Result.fail("比较版本差异失败: " + e.getMessage()));
                });
    }
} 