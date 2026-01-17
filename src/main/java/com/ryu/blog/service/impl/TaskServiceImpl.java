package com.ryu.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.AsyncTaskRepository;
import com.ryu.blog.service.TaskNotificationService;
import com.ryu.blog.service.TaskQueueManager;
import com.ryu.blog.service.TaskQuotaService;
import com.ryu.blog.service.TaskService;
import com.ryu.blog.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 任务服务实现类
 * 
 * @author ryu
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {
    
    private final AsyncTaskRepository taskRepository;
    private final TaskQueueManager queueManager;
    private final TaskQuotaService quotaService;
    private final TaskNotificationService notificationService;
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int COMPLETED_TASK_RETENTION_DAYS = 30;
    private static final int FAILED_TASK_RETENTION_DAYS = 7;
    
    public TaskServiceImpl(AsyncTaskRepository taskRepository, 
                          TaskQueueManager queueManager,
                          TaskQuotaService quotaService,
                          TaskNotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.queueManager = queueManager;
        this.quotaService = quotaService;
        this.notificationService = notificationService;
    }
    
    @Override
    public Mono<Long> submitTask(TaskType taskType, Object request, Long userId, TaskPriority priority) {
        log.info("Submitting task: type={}, userId={}, priority={}", taskType, userId, priority);
        
        // 确定优先级（如果未指定则使用默认值）
        TaskPriority effectivePriority = priority != null ? priority : TaskPriority.NORMAL;
        
        // 检查配额（仅对高优先级任务）
        return quotaService.checkAndConsumeQuota(userId, effectivePriority)
                .then(Mono.defer(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    
                    // 序列化请求参数
                    String requestJson = JsonUtils.serialize(request);
                    if (requestJson == null) {
                        return Mono.error(new BusinessException("Failed to serialize request parameters"));
                    }
                    
                    // 创建任务实体（不设置id，使用数据库自增ID）
                    AsyncTask task = AsyncTask.builder()
                            .userId(userId)
                            .taskType(taskType)
                            .status(TaskStatus.PENDING)
                            .priority(effectivePriority)
                            .requestJson(requestJson)
                            .progress(0)
                            .submitTime(now)
                            .retryCount(0)
                            .maxRetries(DEFAULT_MAX_RETRIES)
                            .build();
                    
                    // 保存到数据库并加入队列
                    return taskRepository.save(task)
                            .flatMap(savedTask -> {
                                Long taskId = savedTask.getId();
                                return queueManager.enqueue(taskId.toString(), savedTask.getPriority(), now)
                                        .thenReturn(taskId);
                            })
                            .doOnSuccess(id -> log.info("Task submitted successfully: taskId={}", id))
                            .doOnError(error -> log.error("Failed to submit task: {}", error.getMessage(), error));
                }));
    }
    
    @Override
    public Mono<AsyncTask> getTaskStatus(Long taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.debug("Getting task status: taskId={}, userId={}", taskId, userId);
        
        return taskRepository.findById(taskId)
                .filter(task -> task.getIsDeleted() == 0)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Task not found: " + taskId)))
                .flatMap(task -> {
                    // 验证用户权限
                    if (!task.getUserId().equals(userId)) {
                        return Mono.error(new PermissionDeniedException("Access denied to task: " + taskId));
                    }
                    return Mono.just(task);
                });
    }
    
    @Override
    public Mono<Object> getTaskResult(Long taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.debug("Getting task result: taskId={}, userId={}", taskId, userId);
        
        return getTaskStatus(taskId)
                .flatMap(task -> {
                    // 检查任务状态
                    if (task.getStatus() != TaskStatus.COMPLETED) {
                        return Mono.error(new BusinessException("Task is not completed yet: " + taskId));
                    }
                    
                    // 反序列化结果
                    Object result = JsonUtils.deserialize(task.getResultJson(), Object.class);
                    if (result == null) {
                        return Mono.error(new BusinessException("Failed to deserialize task result"));
                    }
                    
                    return Mono.just(result);
                });
    }
    
    @Override
    public Mono<Boolean> cancelTask(Long taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("Cancelling task: taskId={}, userId={}", taskId, userId);
        
        return getTaskStatus(taskId)
                .flatMap(task -> {
                    // 只能取消等待中或处理中的任务
                    if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.PROCESSING) {
                        return Mono.error(new BusinessException(
                                "Cannot cancel task in status: " + task.getStatus()));
                    }
                    
                    // 更新任务状态为已取消
                    task.setStatus(TaskStatus.CANCELLED);
                    task.setCompleteTime(LocalDateTime.now());
                    
                    return taskRepository.save(task)
                            .flatMap(savedTask -> {
                                // 从队列中移除
                                return queueManager.remove(taskId.toString())
                                        .then(notificationService.notifyTaskCancelled(savedTask))
                                        .thenReturn(true);
                            });
                })
                .doOnSuccess(result -> log.info("Task cancelled successfully: taskId={}", taskId))
                .doOnError(error -> log.error("Failed to cancel task: taskId={}", taskId, error));
    }
    
    @Override
    public Mono<Long> retryTask(Long taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("Retrying task: taskId={}, userId={}", taskId, userId);
        
        return getTaskStatus(taskId)
                .flatMap(task -> {
                    // 只能重试失败的任务
                    if (task.getStatus() != TaskStatus.FAILED) {
                        return Mono.error(new BusinessException(
                                "Can only retry failed tasks, current status: " + task.getStatus()));
                    }
                    
                    // 创建新任务（复制原任务的参数）
                    return submitTask(
                            task.getTaskType(),
                            JsonUtils.deserialize(task.getRequestJson(), Object.class),
                            userId,
                            task.getPriority()
                    );
                })
                .doOnSuccess(newTaskId -> 
                    log.info("Task retry created: originalTaskId={}, newTaskId={}", taskId, newTaskId)
                );
    }
    
    @Override
    public Flux<AsyncTask> getUserTasks(Long userId, TaskType taskType, Pageable pageable) {
        log.debug("Getting user tasks: userId={}, taskType={}, page={}", 
                userId, taskType, pageable.getPageNumber());
        
        if (taskType != null) {
            return taskRepository.findByUserIdAndTaskType(userId, taskType, pageable);
        } else {
            return taskRepository.findByUserId(userId, pageable);
        }
    }
    
    @Override
    public Mono<Long> countUserTasks(Long userId, TaskType taskType) {
        log.debug("Counting user tasks: userId={}, taskType={}", userId, taskType);
        
        if (taskType != null) {
            return taskRepository.countByUserIdAndTaskType(userId, taskType);
        } else {
            return taskRepository.countByUserId(userId);
        }
    }
    
    @Override
    public Mono<Integer> cleanExpiredTasks() {
        log.info("Starting expired tasks cleanup");
        
        LocalDateTime completedThreshold = LocalDateTime.now().minusDays(COMPLETED_TASK_RETENTION_DAYS);
        LocalDateTime failedThreshold = LocalDateTime.now().minusDays(FAILED_TASK_RETENTION_DAYS);
        
        // 查找过期的已完成任务
        Flux<AsyncTask> expiredCompletedTasks = taskRepository.findByStatus(TaskStatus.COMPLETED, Pageable.unpaged())
                .filter(task -> task.getCompleteTime() != null && 
                        task.getCompleteTime().isBefore(completedThreshold));
        
        // 查找过期的失败任务
        Flux<AsyncTask> expiredFailedTasks = taskRepository.findByStatus(TaskStatus.FAILED, Pageable.unpaged())
                .filter(task -> task.getCompleteTime() != null && 
                        task.getCompleteTime().isBefore(failedThreshold));
        
        // 合并并删除
        return Flux.concat(expiredCompletedTasks, expiredFailedTasks)
                .flatMap(task -> {
                    log.debug("Deleting expired task: taskId={}, status={}, completeTime={}", 
                            task.getTaskId(), task.getStatus(), task.getCompleteTime());
                    return taskRepository.delete(task);
                })
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> log.info("Cleaned up {} expired tasks", count))
                .doOnError(error -> log.error("Failed to clean expired tasks", error));
    }
}
