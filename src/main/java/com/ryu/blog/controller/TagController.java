package com.ryu.blog.controller;

import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.dto.TagCreateDTO;
import com.ryu.blog.dto.TagUpdateDTO;
import com.ryu.blog.service.TagService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 标签控制器
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理", description = "标签相关接口")
public class TagController {

    private final TagService tagService;

    @Operation(summary = "创建标签", description = "创建新标签")
    @PostMapping("/save")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Result<String>> createTag(@RequestBody @Validated TagCreateDTO tagCreateDTO) {
        log.info("创建标签请求: {}", tagCreateDTO);
        return tagService.createTag(tagCreateDTO)
                .doOnSuccess(success -> log.info("标签创建成功: {}", success))
                .map(success -> Result.success(MessageConstants.TAG_CREATE_SUCCESS))
                .doOnError(e -> log.error("创建标签失败: {}", e.getMessage(), e))
                .doFinally(signalType -> log.info("创建标签请求处理完成，结果: {}", signalType));
    }

    @Operation(summary = "更新标签", description = "更新已有标签")
    @PutMapping("/edit")
    public Mono<Result<String>> updateTag(@RequestBody @Validated TagUpdateDTO tagUpdateDTO) {
        log.info("更新标签请求: {}", tagUpdateDTO);
        return tagService.updateTag(tagUpdateDTO)
                .doOnSuccess(success -> log.info("标签更新成功: {}", success))
                .map(success -> Result.success(MessageConstants.TAG_UPDATE_SUCCESS))
                .doOnError(e -> log.error("更新标签失败: {}", e.getMessage(), e));
    }

    @Operation(summary = "获取标签详情", description = "根据ID获取标签详情")
    @GetMapping("/detail/{id}")
    public Mono<Result<TagVO>> getTagById(@PathVariable Long id) {
        return tagService.getTagById(id)
                .map(Result::success);
    }

    @Operation(summary = "删除标签", description = "根据ID删除标签")
    @DeleteMapping("/delete/{id}")
    public Mono<Result<String>> deleteTag(@PathVariable Long id) {
        log.info("删除标签请求: id={}", id);
        return tagService.deleteTag(id)
                .doOnSuccess(success -> log.info("标签删除成功: id={}", id))
                .thenReturn(Result.success(MessageConstants.TAG_DELETE_SUCCESS))
                .doOnError(e -> log.error("删除标签失败: {}", e.getMessage(), e));
    }

        @Operation(summary = "获取所有标签", description = "获取所有标签列表")
    @GetMapping("/list")
    public Mono<Result<List<TagVO>>> getAllTags(
            @Parameter(description = "是否包含文章数量") @RequestParam(defaultValue = "false") boolean withCount) {
        return tagService.getAllTags(withCount)
                .collectList()
                .map(Result::success);
    }

    @Operation(summary = "获取文章标签", description = "根据文章ID获取标签列表")
    @GetMapping("/article/{articleId}")
    public Mono<Result<List<TagVO>>> getTagsByArticleId(@PathVariable Long articleId) {
        return tagService.getTagsByArticleId(articleId)
                .collectList()
                .map(Result::success);
    }

    @Operation(summary = "为文章添加标签", description = "为文章添加标签")
    @PostMapping("/article/{articleId}")
    public Mono<Result<String>> addTagsToArticle(@PathVariable Long articleId, @RequestBody List<Long> tagIds) {
        log.info("为文章添加标签请求: articleId={}, tagIds={}", articleId, tagIds);
        return tagService.addTagsToArticle(articleId, tagIds)
                .doOnSuccess(success -> log.info("文章添加标签成功: articleId={}, 结果={}", articleId, success))
                .map(success -> Result.success(MessageConstants.TAG_ASSIGN_SUCCESS))
                .doOnError(e -> log.error("为文章添加标签失败: {}", e.getMessage(), e));
    }

    @Operation(summary = "移除文章标签", description = "移除文章的所有标签")
    @DeleteMapping("/article/{articleId}")
    public Mono<Result<String>> removeTagsFromArticle(@PathVariable Long articleId) {
        log.info("移除文章标签请求: articleId={}", articleId);
        return tagService.removeTagsFromArticle(articleId)
                .doOnSuccess(success -> log.info("文章标签移除成功: articleId={}, 结果={}", articleId, success))
                .map(success -> Result.success(MessageConstants.TAG_REMOVE_SUCCESS))
                .doOnError(e -> log.error("移除文章标签失败: {}", e.getMessage(), e));
    }

    @Operation(summary = "检查标签名称", description = "检查标签名称是否已存在")
    @GetMapping("/check")
    public Mono<Result<Boolean>> checkTagName(@RequestParam String name) {
        return tagService.checkTagNameExists(name)
                .map(Result::success);
    }

    @Operation(summary = "获取热门标签", description = "获取热门标签列表")
    @GetMapping("/hot")
    public Mono<Result<List<TagVO>>> getHotTags(
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "10") int limit) {
        return tagService.getHotTags(limit)
                .collectList()
                .map(Result::success);
    }
} 