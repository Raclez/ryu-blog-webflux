package com.ryu.blog.service;

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
                            // 不在这个阶段过滤状态：executeTask 会再读一次并决定是否执行，
                            // 那里同时负责 ack。这里过滤掉会让任务一直留在 processing
                            // 集合里（没人 ack），超时后被反复重新入队 —— 死循环。
                            return taskRepository.findById(taskId)
                                    .switchIfEmpty(Mono.defer(() -> {
                                        log.warn("Dequeued task not found in DB, acking: taskId={}", taskIdStr);
                                        return queueManager.ack(taskIdStr)
                                                .then(Mono.<AsyncTask>empty());
                                    }));
                        } catch (NumberFormatException e) {
                            log.error("Invalid task ID format: {}, acking", taskIdStr);
                            return queueManager.ack(taskIdStr)
                                    .then(Mono.<AsyncTask>empty());
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

        // 增加运行任务计数 —— 只加一次，且只由 doFinally 减一次。
        // 原先 onErrorResume 里又减了一次（doFinally 在上游，错误时同样会执行），
        // 失败一次净减 2 → runningTasks 单调变负 → getAvailableSlots() 越来越大，
        // 并发上限彻底失效。
        runningTasks.incrementAndGet();

        // 出队后、置为 PROCESSING 前再读一次数据库状态：
        // 用户可能在任务被 dequeue 之后、执行之前点了取消，此时必须放弃执行，
        // 而不是用旧的实体副本把 CANCELLED 覆盖回 PROCESSING。
        return taskRepository.findById(task.getId())
                .filter(current -> Boolean.FALSE.equals(current.getIsDeleted()))
                .filter(current -> current.getStatus() == TaskStatus.PENDING)
                .flatMap(this::doExecuteTask)
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.info("Task skipped: it is no longer pending (cancelled or already processed), taskId={}", taskId)).then())
                .doFinally(signalType -> {
                    int remaining = runningTasks.decrementAndGet();
                    log.debug("Task finished: taskId={}, signal={}, remaining tasks={}",
                            taskId, signalType, remaining);
                    // 无论成功、失败还是跳过，都要把任务从 processing 集合里移除，
                    // 否则可见性超时后会把它当成「执行者已崩溃」重复投递。
                    queueManager.ack(taskId)
                            .subscribe(acked -> {}, ackError ->
                                    log.warn("Ack task failed: taskId={}, error={}", taskId, ackError.getMessage()));
                })
                .onErrorResume(error -> {
                    log.error("Unexpected error executing task: taskId={}", taskId, error);
                    return Mono.empty();
                });
    }

    /**
     * 执行已经确认仍处于 PENDING 的任务
     */
    private Mono<Void> doExecuteTask(AsyncTask task) {
        String taskId = task.getTaskId();

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
                                    // 5. 写回结果前再确认一次：任务可能已被用户取消，
                                    //    不能把用户的「已取消」覆盖成「已完成」。
                                    String resultJson = JsonUtils.serialize(result);
                                    return taskRepository.findById(updatedTask.getId())
                                            .filter(current -> current.getStatus() != TaskStatus.CANCELLED)
                                            .flatMap(current -> {
                                                current.setResultJson(resultJson);
                                                current.setStatus(TaskStatus.COMPLETED);
                                                current.setCompleteTime(LocalDateTime.now());
                                                current.setProgress(100);
                                                return taskRepository.save(current);
                                            })
                                            .flatMap(saved -> {
                                                log.info("Task completed successfully: taskId={}", taskId);
                                                // 通知文案由 handler 自己给（未声明则用默认文案）。
                                                // 「文章《…》已发布」这类信息只有 handler 知道，
                                                // 放在这里硬编码等于每加一种任务类型都要改执行器。
                                                String message = handler.successMessage(result);
                                                return notificationService.notifyTaskCompleted(saved, message);
                                            })
                                            .switchIfEmpty(Mono.fromRunnable(() ->
                                                    log.info("Task finished after being cancelled, result discarded: taskId={}", taskId)).then());
                                })
                                .onErrorResume(error -> {
                                    log.error("Task execution failed: taskId={}", taskId, error);
                                    return handleTaskFailure(updatedTask, error);
                                });
                    } catch (Exception e) {
                        log.error("Error preparing task execution: taskId={}", taskId, e);
                        return handleTaskFailure(updatedTask, e);
                    }
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

        // 与完成路径同理：已被用户取消的任务不能被改写为 FAILED
        return taskRepository.findById(task.getId())
                .filter(current -> current.getStatus() != TaskStatus.CANCELLED)
                .flatMap(current -> {
                    current.setStatus(TaskStatus.FAILED);
                    current.setCompleteTime(LocalDateTime.now());
                    current.setErrorMessage(error.getMessage() != null
                            ? error.getMessage() : error.getClass().getSimpleName());
                    return taskRepository.save(current);
                })
                .flatMap(saved -> {
                    log.info("Task marked as failed: taskId={}, error={}", taskId, error.getMessage());

                    // 判断是否需要自动重试
                    if (shouldAutoRetry(saved)) {
                        log.info("Task eligible for retry: taskId={}, retryCount={}/{}",
                                taskId, saved.getRetryCount(), saved.getMaxRetries());
                        // 根据需求，重试由用户手动触发，所以这里只记录日志
                    }

                    // 发送失败通知（文案优先由 handler 给）
                    return notificationService.notifyTaskFailed(saved, failureMessage(task.getTaskType(), error));
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.info("Task failure ignored because it was cancelled: taskId={}", taskId)).then());
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
     * <p>按 handler 自己声明的类型还原（{@link TaskHandler#getRequestType()}），
     * 不再在这里按任务类型硬编码分支 —— 那种写法每加一种任务类型都要改执行器，
     * 漏改就退化成 Map，泛型擦除后在运行期才炸。
     *
     * @param requestJson 请求JSON
     * @param taskType 任务类型
     * @return 请求对象
     */
    private Object deserializeRequest(String requestJson, TaskType taskType) {
        return handlerRegistry.deserialize(taskType, requestJson);
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
     * 取 handler 声明的失败文案
     *
     * <p>没有注册 handler 时返回 null（走默认文案）—— 不能在这里抛异常：
     * 失败处理本身正是在出错时执行的，再抛一次会把原始错误现场吃掉。
     *
     * @param taskType 任务类型
     * @param error 执行异常
     * @return 失败文案，未声明时返回 null
     */
    private String failureMessage(TaskType taskType, Throwable error) {
        if (!handlerRegistry.hasHandler(taskType)) {
            return null;
        }
        try {
            return handlerRegistry.getHandler(taskType).failureMessage(error);
        } catch (Exception e) {
            log.warn("Failed to resolve failure message for task type {}: {}", taskType, e.getMessage());
            return null;
        }
    }
    
    /**
     * 定时回收可见性超时的任务 - 每分钟一次
     *
     * <p>覆盖「任务已出队、执行进程在写回结果前崩溃」的场景：
     * 这类任务原先既不在队列也不在 DB 进度里，等于永久丢失。
     */
    @Scheduled(fixedDelay = 60000)
    public void recoverExpiredTasks() {
        queueManager.requeueExpired()
                .subscribe(
                        count -> {
                            if (count != null && count > 0) {
                                log.warn("Recovered {} expired task(s) back to the queue", count);
                            }
                        },
                        error -> log.error("Failed to recover expired tasks", error)
                );
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
