package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.service.TaskNotificationService;
import com.ryu.blog.service.TaskService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 任务管理控制器
 * 提供任务的提交、查询、取消、重试等接口
 * 
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Validated
@Tag(name = "任务管理", description = "异步任务管理接口")
public class TaskController {
    
    private final TaskService taskService;
    private final TaskNotificationService notificationService;
    
    /**
     * 提交任务
     * 
     * @param request 任务请求
     * @return 任务ID
     */
    @PostMapping
    @Operation(summary = "提交任务", description = "提交一个异步任务")
    public Mono<Result<Long>> submitTask(
            @Valid @RequestBody TaskSubmitRequest request) {
        
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
     * @return 任务信息
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "查询任务状态", description = "根据任务ID查询任务状态和详情")
    public Mono<Result<AsyncTask>> getTaskStatus(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        
        return taskService.getTaskStatus(taskId)
                .map(Result::success);
    }
    
    /**
     * 获取任务结果
     * 
     * @param taskId 任务ID
     * @return 任务结果
     */
    @GetMapping("/{taskId}/result")
    @Operation(summary = "获取任务结果", description = "获取已完成任务的结果")
    public Mono<Result<Object>> getTaskResult(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        
        return taskService.getTaskResult(taskId)
                .map(Result::success);
    }
    
    /**
     * 取消任务
     * 
     * @param taskId 任务ID
     * @return 是否取消成功
     */
    @DeleteMapping("/{taskId}")
    @Operation(summary = "取消任务", description = "取消正在等待或执行中的任务")
    public Mono<Result<Boolean>> cancelTask(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        
        log.info("Cancelling task: taskId={}", taskId);
        
        return taskService.cancelTask(taskId)
                .map(Result::success)
                .doOnSuccess(result -> log.info("Task cancelled: taskId={}, success={}", 
                        taskId, result.getData()));
    }
    
    /**
     * 重试任务
     * 
     * @param taskId 原任务ID
     * @return 新任务ID
     */
    @PostMapping("/{taskId}/retry")
    @Operation(summary = "重试任务", description = "重试失败的任务，创建新任务")
    public Mono<Result<Long>> retryTask(
            @Parameter(description = "原任务ID") @PathVariable Long taskId) {
        
        log.info("Retrying task: taskId={}", taskId);
        
        return taskService.retryTask(taskId)
                .map(Result::success)
                .doOnSuccess(result -> log.info("Task retry created: originalTaskId={}, newTaskId={}", 
                        taskId, result.getData()));
    }
    
    /**
     * 查询用户任务列表
     * 
     * @param taskType 任务类型（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 任务列表
     */
    @GetMapping
    @Operation(summary = "查询任务列表", description = "分页查询用户的任务列表")
    public Mono<Result<java.util.List<AsyncTask>>> getUserTasks(
            @Parameter(description = "任务类型") @RequestParam(required = false) TaskType taskType,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        
        Long userId = StpUtil.getLoginIdAsLong();
        
        return taskService.getUserTasks(userId, taskType, PageRequest.of(page, size))
                .collectList()
                .map(Result::success);
    }
    
    /**
     * 获取离线通知
     * 
     * @return 离线通知列表
     */
    @GetMapping("/notifications/offline")
    @Operation(summary = "获取离线通知", description = "获取用户的离线任务通知")
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
    @Operation(summary = "清除离线通知", description = "清除用户的所有离线通知")
    public Mono<Result<Boolean>> clearOfflineNotifications() {
        Long userId = StpUtil.getLoginIdAsLong();
        
        return notificationService.clearOfflineNotifications(userId)
                .map(Result::success);
    }
    
    /**
     * 任务提交请求
     */
    @lombok.Data
    public static class TaskSubmitRequest {
        
        @NotNull(message = "任务类型不能为空")
        @Parameter(description = "任务类型", required = true)
        private TaskType taskType;
        
        @NotNull(message = "请求参数不能为空")
        @Parameter(description = "任务请求参数", required = true)
        private Object request;
        
        @Parameter(description = "任务优先级")
        private TaskPriority priority;
    }
}
