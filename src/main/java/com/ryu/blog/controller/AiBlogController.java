package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.entity.AiGenerationHistory;
import com.ryu.blog.service.AiBlogIntegrationService;
import com.ryu.blog.service.AiBlogService;
import com.ryu.blog.service.TaskService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * AI博客控制器
 * 
 * <p>提供AI博客生成、优化、历史记录管理等API接口。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ai/blog")
@RequiredArgsConstructor
@Validated
@Tag(name = "AI博客", description = "AI博客生成和管理接口")
public class AiBlogController {

    private final AiBlogService aiBlogService;
    private final AiBlogIntegrationService aiBlogIntegrationService;
    private final TaskService taskService;

    @PostMapping("/generate")
    @Operation(summary = "生成博客内容", description = "使用AI生成博客内容（非流式）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "生成成功",
                    content = @Content(schema = @Schema(implementation = AiGenerationResult.class))),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "429", description = "超出速率限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<AiGenerationResult>> generateBlogContent(
            @Valid @RequestBody AiGenerationRequest request) {
        log.info("收到博客生成请求: userId={}, mode={}", request.getUserId(), request.getMode());
        return aiBlogService.generateBlogContent(request)
                .map(Result::success);
    }

    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式生成博客内容", description = "使用AI流式生成博客内容（SSE）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "生成成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "429", description = "超出速率限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Flux<ServerSentEvent<String>> generateBlogContentStream(
            @Valid @RequestBody AiGenerationRequest request) {
        log.info("收到流式博客生成请求: userId={}, mode={}", request.getUserId(), request.getMode());
        
        return aiBlogService.generateBlogContentStream(request)
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder()
                        .comment("Stream completed")
                        .build()));
    }

    @PostMapping("/refine")
    @Operation(summary = "优化内容", description = "对已有内容进行优化（扩展、摘要、重写、翻译等）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "优化成功",
                    content = @Content(schema = @Schema(implementation = AiGenerationResult.class))),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "429", description = "超出速率限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<AiGenerationResult>> refineContent(
            @Valid @RequestBody AiGenerationRequest request) {
        log.info("收到内容优化请求: userId={}, hasContent={}", 
                request.getUserId(), request.getContent() != null);
        return aiBlogService.refineContent(request)
                .map(Result::success);
    }

    @GetMapping("/history")
    @Operation(summary = "获取生成历史", description = "获取用户的AI生成历史记录（分页）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<List<AiGenerationHistory>>> getGenerationHistory(
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        log.info("获取生成历史: userId={}, page={}, size={}", userId, page, size);
        return aiBlogService.getGenerationHistory(userId, PageRequest.of(page, size))
                .collectList()
                .map(Result::success);
    }

    @GetMapping("/history/{id}")
    @Operation(summary = "获取历史记录详情", description = "根据ID获取单条历史记录")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "记录不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<AiGenerationHistory>> getHistoryById(
            @Parameter(description = "历史记录ID") @PathVariable Long id,
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("获取历史记录详情: id={}, userId={}", id, userId);
        return aiBlogService.getHistoryById(id, userId)
                .map(Result::success);
    }

    @DeleteMapping("/history/{id}")
    @Operation(summary = "删除历史记录", description = "删除指定的历史记录")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "记录不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<Boolean>> deleteHistory(
            @Parameter(description = "历史记录ID") @PathVariable Long id,
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("删除历史记录: id={}, userId={}", id, userId);
        return aiBlogService.deleteHistory(id, userId)
                .map(Result::success);
    }

    @PostMapping("/history/{id}/regenerate")
    @Operation(summary = "重新生成", description = "基于历史记录重新生成内容")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "生成成功",
                    content = @Content(schema = @Schema(implementation = AiGenerationResult.class))),
            @ApiResponse(responseCode = "404", description = "记录不存在"),
            @ApiResponse(responseCode = "429", description = "超出速率限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<AiGenerationResult>> regenerate(
            @Parameter(description = "历史记录ID") @PathVariable Long id,
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("重新生成内容: historyId={}, userId={}", id, userId);
        return aiBlogService.regenerate(id, userId)
                .map(Result::success);
    }

    @GetMapping("/history/count")
    @Operation(summary = "统计生成次数", description = "获取用户的总生成次数")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<Long>> countGenerations(
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("统计生成次数: userId={}", userId);
        return aiBlogService.countGenerations(userId)
                .map(Result::success);
    }

    @PostMapping("/save-draft")
    @Operation(summary = "保存为草稿", description = "将AI生成的内容保存为博客草稿")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "保存成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<com.ryu.blog.entity.Posts>> saveToDraft(
            @Valid @RequestBody SaveDraftRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("保存AI内容为草稿: userId={}, historyId={}", userId, request.getHistoryId());
        
        return aiBlogService.getHistoryById(request.getHistoryId(), userId)
                .flatMap(history -> {
                    // 从历史记录中获取生成结果
                    AiGenerationResult result = com.ryu.blog.utils.JsonUtils.deserialize(
                            history.getResult(), 
                            AiGenerationResult.class
                    );
                    
                    if (result == null) {
                        log.error("解析生成结果失败: historyId={}", request.getHistoryId());
                        return Mono.error(new com.ryu.blog.exception.BusinessException("解析生成结果失败"));
                    }
                    
                    // 保存为草稿
                    if (request.getCategoryIds() != null && request.getTagIds() != null) {
                        return aiBlogIntegrationService.saveToDraft(
                                result, 
                                userId, 
                                request.getCategoryIds(), 
                                request.getTagIds()
                        );
                    } else {
                        return aiBlogIntegrationService.saveToDraft(result, userId);
                    }
                })
                .map(Result::success);
    }

    /**
     * 保存草稿请求
     */
    @lombok.Data
    public static class SaveDraftRequest {
        @NotNull(message = "历史记录ID不能为空")
        private Long historyId;
        
        private java.util.List<Long> categoryIds;
        private java.util.List<Long> tagIds;
    }
    
    /**
     * 异步生成博客内容
     * 提交任务后立即返回任务ID，后台异步处理
     * 
     * @param request 生成请求
     * @return 任务ID
     */
    @PostMapping("/generate/async")
    @Operation(summary = "异步生成博客内容", description = "提交AI生成任务，立即返回任务ID，后台异步处理")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "任务提交成功，返回任务ID"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "429", description = "超出配额限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<Long>> generateBlogContentAsync(
            @Valid @RequestBody AiGenerationRequest request) {
        // 从 SaToken 获取当前登录用户ID
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("收到异步博客生成请求: userId={}, mode={}", userId, request.getMode());
        
        // 设置 userId 到请求对象中（用于任务执行时使用）
        request.setUserId(userId);
        
        // 提交异步任务
        return taskService.submitTask(
                        com.ryu.blog.enums.TaskType.AI_GENERATION,
                        request,
                        userId,
                        null  // 使用默认优先级
                )
                .map(Result::success)
                .doOnSuccess(result -> log.info("异步任务已提交: taskId={}", result.getData()));
    }
}

