package com.ryu.blog.service;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.repository.AsyncTaskRepository;
import com.ryu.blog.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步任务执行器
 * 定时从 Redis 队列获取任务并执行
 * 
 * <p>特性：
 * <ul>
 *   <li>并发控制，限制同时执行的任务数</li>
 *   <li>任务超时控制</li>
 *   <li>自动失败处理和通知</li>
 *   <li>定时清理过期任务</li>
 * </ul>
 * 
 * @author ryu
 */
@Slf4j
@Component
public class AsyncTaskExecutor {
    
    private final TaskQueueManager queueManager;
    private final TaskHandlerRegistry handlerRegistry;
    private final AsyncTaskRepository taskRepository;
    private final Scheduler taskScheduler;
    private final TaskService taskService;
    private final TaskNotificationService notificationService;
    
    @Value("${task.executor.schedule.batch-size:10}")
    private int batchSize;
    
    @Value("${task.executor.timeout.default:600000}")
    private long defaultTimeoutMs;
    
    // 当前正在执行的任务数（单体应用使用本地计数器即可）
    private final AtomicInteger runningTasks = new AtomicInteger(0);
    
    // 最大并发任务数
    private static final int MAX_CONCURRENT_TASKS = 10;
    
    public AsyncTaskExecutor(
            TaskQueueManager queueManager,
            TaskHandlerRegistry handlerRegistry,
            AsyncTaskRepository taskRepository,
            @Qualifier("asyncTaskScheduler") Scheduler taskScheduler,
            TaskService taskService,
            TaskNotificationService notificationService) {
        this.queueManager = queueManager;
        this.handlerRegistry = handlerRegistry;
        this.taskRepository = taskRepository;
        this.taskScheduler = taskScheduler;
        this.taskService = taskService;
        this.notificationService = notificationService;
    }
    
    /**
     * 定时处理任务 - 每5秒执行一次
     */
    @Scheduled(fixedDelay = 5000)
    public void processTasks() {
        try {
            // 获取可用的并发槽位
            int availableSlots = getAvailableSlots();
            
            if (availableSlots <= 0) {
                log.debug("No available slots, skipping task processing. Running tasks: {}", 
                        runningTasks.get());
                return;
            }
            
            log.debug("Processing tasks: available slots = {}, running tasks = {}", 
                    availableSlots, runningTasks.get());
            
            // 从 Redis 队列批量获取任务
            queueManager.dequeue(availableSlots)
                    .flatMap(taskIdStr -> {
                        try {
                            Long taskId = Long.parseLong(taskIdStr);
                            return taskRepository.findById(taskId)
                                    .filter(task -> task.getIsDeleted() == 0)
                                    .filter(task -> task.getStatus() == TaskStatus.PENDING);
                        } catch (NumberFormatException e) {
                            log.error("Invalid task ID format: {}", taskIdStr);
                            return Mono.empty();
                        }
                    })
                    .flatMap(this::executeTask)
                    .subscribe(
                            result -> {},
                            error -> log.error("Error processing tasks", error),
                            () -> log.debug("Task processing batch completed")
                    );
        } catch (Exception e) {
            log.error("Unexpected error in task processing", e);
        }
    }
    
    /**
     * 获取可用的并发槽位
     * 
     * @return 可用槽位数
     */
    private int getAvailableSlots() {
        int running = runningTasks.get();
        int available = MAX_CONCURRENT_TASKS - running;
        return Math.max(0, available);
    }
    
    /**
     * 执行单个任务（完全响应式）
     * 
     * @param task 任务实体
     * @return Mono<Void>
     */
    private Mono<Void> executeTask(AsyncTask task) {
        String taskId = task.getTaskId();
        log.info("Executing task: taskId={}, type={}", taskId, task.getTaskType());
        
        // 增加运行任务计数
        runningTasks.incrementAndGet();
        
        // 1. 更新任务状态为 PROCESSING
        return updateTaskStatus(task, TaskStatus.PROCESSING)
                .flatMap(updatedTask -> {
                    try {
                        // 2. 获取对应的 TaskHandler
                        TaskHandler<Object, Object> handler = handlerRegistry.getHandler(task.getTaskType());
                        
                        // 3. 反序列化请求参数
                        Object request = deserializeRequest(task.getRequestJson(), task.getTaskType());
                        
                        // 4. 执行任务（带超时控制）
                        Duration timeout = Duration.ofMillis(defaultTimeoutMs);
                        
                        return handler.execute(request)
                                .timeout(timeout)
                                .subscribeOn(taskScheduler)
                                .flatMap(result -> {
                                    // 5. 保存结果并更新状态为 COMPLETED
                                    String resultJson = JsonUtils.serialize(result);
                                    updatedTask.setResultJson(resultJson);
                                    updatedTask.setStatus(TaskStatus.COMPLETED);
                                    updatedTask.setCompleteTime(LocalDateTime.now());
                                    updatedTask.setProgress(100);
                                    
                                    return taskRepository.save(updatedTask)
                                            .flatMap(saved -> {
                                                log.info("Task completed successfully: taskId={}", taskId);
                                                return notificationService.notifyTaskCompleted(saved);
                                            });
                                })
                                .onErrorResume(error -> {
                                    log.error("Task execution failed: taskId={}", taskId, error);
                                    return handleTaskFailure(updatedTask, error);
                                });
                    } catch (Exception e) {
                        log.error("Error preparing task execution: taskId={}", taskId, e);
                        return handleTaskFailure(updatedTask, e);
                    }
                })
                .doFinally(signalType -> {
                    // 减少运行任务计数
                    int remaining = runningTasks.decrementAndGet();
                    log.debug("Task finished: taskId={}, signal={}, remaining tasks={}", 
                            taskId, signalType, remaining);
                })
                .onErrorResume(error -> {
                    log.error("Unexpected error executing task: taskId={}", taskId, error);
                    // 确保计数器被减少
                    runningTasks.decrementAndGet();
                    return Mono.empty();
                });
    }
    
    /**
     * 处理任务失败
     * 
     * @param task 任务实体
     * @param error 错误信息
     * @return Mono<Void>
     */
    private Mono<Void> handleTaskFailure(AsyncTask task, Throwable error) {
        String taskId = task.getTaskId();
        log.error("Handling task failure: taskId={}", taskId, error);
        
        // 更新任务状态为 FAILED
        task.setStatus(TaskStatus.FAILED);
        task.setCompleteTime(LocalDateTime.now());
        task.setErrorMessage(error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
        
        return taskRepository.save(task)
                .flatMap(saved -> {
                    log.info("Task marked as failed: taskId={}, error={}", taskId, error.getMessage());
                    
                    // 判断是否需要自动重试
                    if (shouldAutoRetry(saved)) {
                        log.info("Task eligible for retry: taskId={}, retryCount={}/{}", 
                                taskId, saved.getRetryCount(), saved.getMaxRetries());
                        // 根据需求，重试由用户手动触发，所以这里只记录日志
                    }
                    
                    // 发送失败通知
                    return notificationService.notifyTaskFailed(saved);
                });
    }
    
    /**
     * 更新任务状态
     * 
     * @param task 任务实体
     * @param status 新状态
     * @return Mono<AsyncTask>
     */
    private Mono<AsyncTask> updateTaskStatus(AsyncTask task, TaskStatus status) {
        task.setStatus(status);
        
        if (status == TaskStatus.PROCESSING) {
            task.setStartTime(LocalDateTime.now());
        }
        
        return taskRepository.save(task)
                .doOnSuccess(saved -> 
                    log.debug("Task status updated: taskId={}, status={}", task.getTaskId(), status)
                );
    }
    
    /**
     * 反序列化请求参数
     * 
     * @param requestJson 请求JSON
     * @param taskType 任务类型
     * @return 请求对象
     */
    private Object deserializeRequest(String requestJson, TaskType taskType) {
        if (taskType == TaskType.AI_GENERATION) {
            return JsonUtils.deserialize(requestJson, AiGenerationRequest.class);
        }
        // 其他任务类型可以在这里添加
        // 默认返回 Map
        return JsonUtils.toMap(requestJson);
    }
    
    /**
     * 判断是否应该自动重试
     * 
     * @param task 任务实体
     * @return 是否应该重试
     */
    private boolean shouldAutoRetry(AsyncTask task) {
        return task.getRetryCount() < task.getMaxRetries();
    }
    
    /**
     * 定时清理过期任务 - 每天凌晨2点执行
     */
    @Scheduled(cron = "${task.executor.cleanup.cron:0 0 2 * * ?}")
    public void cleanExpiredTasks() {
        log.info("Starting scheduled task cleanup");
        
        taskService.cleanExpiredTasks()
                .subscribe(
                        count -> log.info("Scheduled cleanup completed: {} tasks cleaned", count),
                        error -> log.error("Scheduled cleanup failed", error)
                );
    }
}
