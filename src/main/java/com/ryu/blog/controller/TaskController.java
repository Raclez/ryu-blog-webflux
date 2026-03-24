package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.service.TaskNotificationService;
import com.ryu.blog.service.TaskService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * 统一任务管理控制器
 * 提供所有类型异步任务的统一管理接口，包括任务提交、查询、取消、重试等功能
 * 支持的任务类型包括：AI_GENERATION（AI内容生成）等
 * 
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
@Tag(name = "任务管理", description = "统一的异步任务管理接口，支持所有任务类型（AI_GENERATION等）")
public class TaskController {
    
    private final TaskService taskService;
    private final TaskNotificationService notificationService;
    
    /**
     * 提交任务
     * 支持所有任务类型，包括 AI_GENERATION（AI内容生成）等
     *
     * @param request 任务请求，包含任务类型、请求参数和优先级
     * @return 创建的任务ID
     */
    @PostMapping
    @Operation(
        summary = "提交任务",
        description = "提交一个异步任务。支持所有任务类型：AI_GENERATION（AI内容生成）等。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "任务提交成功",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Result.class),
                examples = @ExampleObject(
                    value = "{\"code\":200,\"message\":\"success\",\"data\":12345}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数错误",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\"code\":400,\"message\":\"任务类型不能为空\",\"data\":null}"
                )
            )
        )
    })
    public Mono<Result<Long>> submitTask(
            @Valid @RequestBody 
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "任务提交请求",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    examples = {
                        @ExampleObject(
                            name = "AI生成任务示例",
                            value = "{\"taskType\":\"AI_GENERATION\",\"request\":{\"prompt\":\"写一篇关于Spring Boot的技术文章\",\"maxTokens\":2000},\"priority\":\"HIGH\"}"
                        )
                    }
                )
            )
            TaskSubmitRequest request) {
        
        Long userId = StpUtil.getLoginIdAsLong();

        log.info("Submitting task: type={}, userId={}, priority={}", 
                request.getTaskType(), userId, request.getPriority());
        
        return taskService.submitTask(
                        request.getTaskType(),
                        request.getRequest(),
                        userId,
                        request.getPriority())
                .map(Result::success)
                .doOnSuccess(result -> log.info("Task submitted: taskId={}", result.getData()));
    }
    
    /**
     * 查询任务状态
     * 
     * @param taskId 任务ID
     * @return 任务详细信息，包括状态、进度、结果等
     */
    @GetMapping("/{taskId}")
    @Operation(
        summary = "查询任务状态", 
        description = "根据任务ID查询任务的详细状态和信息"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AsyncTask.class),
                examples = @ExampleObject(
                    value = "{\"code\":200,\"message\":\"success\",\"data\":{\"id\":12345,\"taskType\":\"AI_GENERATION\",\"status\":\"COMPLETED\",\"progress\":100,\"result\":\"{\\\"content\\\":\\\"生成内容\\\"}\"}}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "任务不存在"
        )
    })
    public Mono<Result<AsyncTask>> getTaskStatus(
            @Parameter(description = "任务ID", required = true, example = "12345") 
            @PathVariable Long taskId) {
        
        return taskService.getTaskStatus(taskId)
                .map(Result::success);
    }
    
    /**
     * 获取任务结果
     * 
     * @param taskId 任务ID
     * @return 任务执行结果（JSON格式）
     */
    @GetMapping("/{taskId}/result")
    @Operation(
        summary = "获取任务结果", 
        description = "获取已完成任务的执行结果。结果格式取决于任务类型。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(
                        name = "AI生成任务结果",
                        value = "{\"code\":200,\"message\":\"success\",\"data\":{\"content\":\"生成的文章内容...\",\"tokensUsed\":1500}}"
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "任务不存在或未完成"
        )
    })
    public Mono<Result<Object>> getTaskResult(
            @Parameter(description = "任务ID", required = true, example = "12345") 
            @PathVariable Long taskId) {
        
        return taskService.getTaskResult(taskId)
                .map(Result::success);
    }
    
    /**
     * 取消任务
     * 只能取消状态为 PENDING（等待中）或 RUNNING（执行中）的任务
     * 
     * @param taskId 任务ID
     * @return 是否取消成功
     */
    @DeleteMapping("/{taskId}")
    @Operation(
        summary = "取消任务", 
        description = "取消正在等待或执行中的任务。已完成或已失败的任务无法取消。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取消成功",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\"code\":200,\"message\":\"success\",\"data\":true}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "任务状态不允许取消"
        )
    })
    public Mono<Result<Boolean>> cancelTask(
            @Parameter(description = "任务ID", required = true, example = "12345") 
            @PathVariable Long taskId) {
        
        log.info("Cancelling task: taskId={}", taskId);
        
        return taskService.cancelTask(taskId)
                .map(Result::success)
                .doOnSuccess(result -> log.info("Task cancelled: taskId={}, success={}", 
                        taskId, result.getData()));
    }
    
    /**
     * 重试任务
     * 创建一个新任务，使用原任务的参数重新执行
     * 
     * @param taskId 原任务ID
     * @return 新创建的任务ID
     */
    @PostMapping("/{taskId}/retry")
    @Operation(
        summary = "重试任务", 
        description = "重试失败的任务。系统会创建一个新任务，使用原任务的参数重新执行。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "重试任务创建成功",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\"code\":200,\"message\":\"success\",\"data\":12346}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "原任务不存在"
        )
    })
    public Mono<Result<Long>> retryTask(
            @Parameter(description = "原任务ID", required = true, example = "12345") 
            @PathVariable Long taskId) {
        
        log.info("Retrying task: taskId={}", taskId);
        
        return taskService.retryTask(taskId)
                .map(Result::success)
                .doOnSuccess(result -> log.info("Task retry created: originalTaskId={}, newTaskId={}", 
                        taskId, result.getData()));
    }
    
    /**
     * 查询用户任务列表
     * 支持按任务类型过滤和分页查询
     * 
     * @param taskType 任务类型（可选），支持：AI_GENERATION等
     * @param current 当前页码（从1开始）
     * @param size 每页大小
     * @return 任务分页列表
     */
    @GetMapping
    @Operation(
        summary = "查询任务列表", 
        description = "分页查询当前用户的任务列表。支持按任务类型过滤。支持所有任务类型：AI_GENERATION等。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\"code\":200,\"message\":\"success\",\"data\":{\"records\":[{\"id\":12345,\"taskType\":\"AI_GENERATION\",\"status\":\"COMPLETED\"}],\"total\":1,\"size\":20,\"current\":1}}"
                )
            )
        )
    })
    public Mono<Result<PageResult<AsyncTask>>> getUserTasks(
            @Parameter(
                description = "任务类型（可选）。支持：AI_GENERATION（AI内容生成）等",
                example = "AI_GENERATION"
            ) 
            @RequestParam(required = false) TaskType taskType,
            @Parameter(description = "当前页码，从1开始", example = "1") 
            @RequestParam(defaultValue = "1") int current,
            @Parameter(description = "每页大小", example = "20") 
            @RequestParam(defaultValue = "20") int size) {
        
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 转换为0基页码
        int page = current - 1;
        
        return taskService.getUserTasks(userId, taskType, PageRequest.of(page, size))
                .collectList()
                .flatMap(tasks -> {
                    // 获取总数
                    Mono<Long> totalMono = taskService.countUserTasks(userId, taskType);
                    return totalMono.map(total -> {
                        PageResult<AsyncTask> pageResult = new PageResult<>(tasks, total, size, current);
                        return pageResult;
                    });
                })
                .map(Result::success);
    }
    
    /**
     * 获取离线通知
     * 
     * @return 离线通知列表
     */
    @GetMapping("/notifications/offline")
    @Operation(
        summary = "获取离线通知", 
        description = "获取用户的离线任务通知。当用户不在线时，任务完成的通知会被保存，用户上线后可以获取。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\"code\":200,\"message\":\"success\",\"data\":[\"任务12345已完成\",\"任务12346执行失败\"]}"
                )
            )
        )
    })
    public Mono<Result<java.util.List<String>>> getOfflineNotifications() {
        Long userId = StpUtil.getLoginIdAsLong();
        
        return notificationService.getOfflineNotifications(userId)
                .map(Result::success);
    }
    
    /**
     * 清除离线通知
     * 
     * @return 是否清除成功
     */
    @DeleteMapping("/notifications/offline")
    @Operation(
        summary = "清除离线通知", 
        description = "清除用户的所有离线通知"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "清除成功",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\"code\":200,\"message\":\"success\",\"data\":true}"
                )
            )
        )
    })
    public Mono<Result<Boolean>> clearOfflineNotifications() {
        Long userId = StpUtil.getLoginIdAsLong();
        
        return notificationService.clearOfflineNotifications(userId)
                .map(Result::success);
    }
    
    /**
     * 任务提交请求
     */
    @lombok.Data
    @Schema(description = "任务提交请求")
    public static class TaskSubmitRequest {
        
        @NotNull(message = "任务类型不能为空")
        @Parameter(
            description = "任务类型。支持：AI_GENERATION（AI内容生成）等",
            required = true,
            example = "AI_GENERATION"
        )
        @Schema(description = "任务类型", example = "AI_GENERATION", required = true)
        private TaskType taskType;
        
        @NotNull(message = "请求参数不能为空")
        @Parameter(
            description = "任务请求参数，格式取决于任务类型", 
            required = true
        )
        @Schema(description = "任务请求参数（JSON对象）", required = true)
        private Object request;
        
        @Parameter(
            description = "任务优先级。可选值：LOW（低）、NORMAL（普通）、HIGH（高）、URGENT（紧急）。默认：NORMAL", 
            example = "NORMAL"
        )
        @Schema(description = "任务优先级", example = "NORMAL", defaultValue = "NORMAL")
        private TaskPriority priority;
    }
}
