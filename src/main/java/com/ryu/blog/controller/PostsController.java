package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostQueryDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.service.ArticleService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PostDetailVO;
import com.ryu.blog.vo.PostFrontListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 博客文章控制器
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "文章管理", description = "文章相关接口")
public class PostsController {

    private final ArticleService articleService;

    /**
     * 后台管理分页查询文章列表
     */
    @Operation(summary = "分页查询文章列表", description = "后台管理分页查询文章列表")
    @GetMapping("/page")
    public Mono<Result<Map<String, Object>>> getPostsPage(
            @ParameterObject @Validated PostQueryDTO query) {
        return articleService.getArticlePage(
                query.getCurrent() - 1, 
                query.getSize(), 
                query.getTitle(), 
                query.getStatus(), 
                query.getCategoryId(), 
                query.getTagId(), 
                query.getStartTime(), 
                query.getEndTime()
            )
            .map(Result::success)
            .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 前台游标方式加载文章列表
     */
    @Operation(summary = "前台游标加载文章列表", description = "前台游标方式加载文章列表（适用于无限滚动加载）")
    @GetMapping("/front")
    public Mono<Result<Map<String, Object>>> getFrontPosts(
            @Parameter(description = "游标ID") @RequestParam(required = false) String cursor,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "5") int limit,
            @Parameter(description = "基准创建时间") @RequestParam(required = false) String createTime,
            @Parameter(description = "加载方向") @RequestParam(defaultValue = "comprehensive") String direction) {
        return articleService.getFrontArticles(cursor, limit, createTime, direction)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 创建文章
     */
    @Operation(summary = "创建文章", description = "创建新文章")
    @PostMapping("/save")
    public Mono<Result<Void>> createPost(@RequestBody @Validated PostCreateDTO postCreateDTO) {
        // 设置作者ID为当前登录用户
        return articleService.createArticle(postCreateDTO, StpUtil.getLoginIdAsLong())
                .then(Mono.defer(() -> Mono.just(Result.<Void>success())))
                .onErrorResume(e -> Mono.just(Result.<Void>error(e.getMessage())));
    }

    /**
     * 更新文章
     */
    @Operation(summary = "更新文章", description = "更新已有文章")
    @PutMapping("/edit")
    public Mono<Result<Void>> updatePost(@RequestBody @Validated PostUpdateDTO postUpdateDTO) {
        return articleService.updateArticle(postUpdateDTO)
                .then(Mono.defer(() -> Mono.just(Result.<Void>success())))
                .onErrorResume(e -> Mono.just(Result.<Void>error(e.getMessage())));
    }

    /**
     * 获取文章详情
     */
    @Operation(summary = "获取文章详情", description = "根据ID获取文章详情")
    @GetMapping("/detail/{id}")
    public Mono<Result<PostDetailVO>> getPostDetail(@PathVariable Long id) {
        return articleService.getArticleDetailVO(id)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 批量删除文章
     */
    @Operation(summary = "批量删除文章", description = "批量删除文章")
    @PostMapping("/delete/batch")
    public Mono<Result<Void>> batchDeletePosts(@RequestBody List<String> ids) {
        return articleService.batchDeleteArticles(ids)
                .then(Mono.defer(() -> Mono.just(Result.<Void>success())))
                .onErrorResume(e -> Mono.just(Result.<Void>error(e.getMessage())));
    }

    /**
     * 获取相关博客推荐
     */
    @Operation(summary = "获取相关博客推荐", description = "获取相关博客推荐")
    @GetMapping("/related/{postId}/{limit}")
    public Mono<Result<List<PostFrontListVO>>> getRelatedPosts(
            @PathVariable Long postId,
            @PathVariable Integer limit) {
        return articleService.getRelatedArticlesVO(postId, limit)
                .collectList()
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
    
    /**
     * 更新文章状态
     */
    @Operation(summary = "更新文章状态", description = "更新文章状态")
    @PutMapping("/status")
    public Mono<Result<Void>> updatePostStatus(@RequestBody @Validated PostStatusDTO statusDTO) {
        return articleService.updateArticleStatus(statusDTO)
                .then(Mono.defer(() -> Mono.just(Result.<Void>success())))
                .onErrorResume(e -> Mono.just(Result.<Void>error(e.getMessage())));
    }
}
