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
import com.ryu.blog.service.TaskHandlerRegistry;
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
    private final TaskHandlerRegistry handlerRegistry;

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int COMPLETED_TASK_RETENTION_DAYS = 30;
    private static final int FAILED_TASK_RETENTION_DAYS = 7;

    public TaskServiceImpl(AsyncTaskRepository taskRepository,
                          TaskQueueManager queueManager,
                          TaskQuotaService quotaService,
                          TaskNotificationService notificationService,
                          TaskHandlerRegistry handlerRegistry) {
        this.taskRepository = taskRepository;
        this.queueManager = queueManager;
        this.quotaService = quotaService;
        this.notificationService = notificationService;
        this.handlerRegistry = handlerRegistry;
    }
    
    @Override
    public Mono<Long> submitTask(TaskType taskType, Object request, Long userId, TaskPriority priority) {
        return submitTask(taskType, request, userId, priority, null);
    }

    @Override
    public Mono<Long> submitTask(TaskType taskType, Object request, Long userId, TaskPriority priority,
                                 LocalDateTime executeAt) {
        log.info("Submitting task: type={}, userId={}, priority={}, executeAt={}", taskType, userId, priority, executeAt);

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
                            // 计划执行时间落库：它是 Redis 丢数据后重建延迟任务的唯一依据
                            .executeAt(executeAt)
                            .retryCount(0)
                            .maxRetries(DEFAULT_MAX_RETRIES)
                            .build();

                    // 保存到数据库并加入队列
                    return taskRepository.save(task)
                            .flatMap(savedTask -> {
                                Long taskId = savedTask.getId();
                                // 延迟任务进延迟区，不进主队列：主队列的出队脚本没有到期闸门
                                Mono<Void> enqueue = executeAt != null
                                        ? queueManager.enqueueScheduled(taskId.toString(), executeAt)
                                        : queueManager.enqueue(taskId.toString(), savedTask.getPriority(), now);
                                return enqueue
                                        .thenReturn(taskId)
                                        // 入队失败不能留下孤立的 PENDING 行（永远不会有人调度它）：
                                        // 落库成功但入队失败时，把任务标成 FAILED 再向上抛错。
                                        .onErrorResume(enqueueError -> {
                                            log.error("Failed to enqueue task {}, marking as FAILED", taskId, enqueueError);
                                            savedTask.setStatus(TaskStatus.FAILED);
                                            savedTask.setCompleteTime(LocalDateTime.now());
                                            savedTask.setErrorMessage("任务入队失败: " + enqueueError.getMessage());
                                            return taskRepository.save(savedTask)
                                                    .then(Mono.error(new BusinessException("任务入队失败，请稍后重试")));
                                        });
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
                .filter(task -> Boolean.FALSE.equals(task.getIsDeleted()))
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
        return cancelTask(taskId, userId);
    }

    /**
     * 取消任务（显式指定操作者）
     *
     * <p>与 {@link #cancelTask(Long)} 的区别只在登录态来源：后者靠
     * {@code StpUtil.getLoginIdAsLong()}，而 Sa-Token 的 Reactor 上下文靠上下文传播，
     * 在响应式链的 flatMap 里可能已经取不到。业务域调用一律走这个重载。
     */
    @Override
    public Mono<Boolean> cancelTask(Long taskId, Long userId) {
        log.info("Cancelling task: taskId={}, userId={}", taskId, userId);

        if (userId == null) {
            return Mono.error(new PermissionDeniedException("Access denied to task: " + taskId));
        }

        return taskRepository.findById(taskId)
                .filter(task -> Boolean.FALSE.equals(task.getIsDeleted()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Task not found: " + taskId)))
                .flatMap(task -> {
                    // 归属校验：只能取消自己的任务
                    if (!task.getUserId().equals(userId)) {
                        return Mono.error(new PermissionDeniedException("Access denied to task: " + taskId));
                    }

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
                                // 从队列中移除（待执行队列与延迟区都会清掉）
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
                    // 反序列化走注册器：按 handler 声明的类型还原，
                    // 否则重试出来的任务拿到的是 Map，handler 会炸。
                    //
                    // ⚠️ 计划执行时间要一起带过去：
                    // 原任务如果是「还没到点就失败」的定时任务（比如刚提交就挂了），
                    // 重试时丢了 execute_at 就会立刻执行 —— 定时发布会提前放出文章。
                    // 已经过了点的任务（绝大多数失败任务都是这样）则直接立即执行：
                    // 它的计划时间已经作废，再塞回延迟区只是白白等 promoter 捞一次。
                    LocalDateTime originalExecuteAt = task.getExecuteAt();
                    LocalDateTime retryExecuteAt = (originalExecuteAt != null
                            && originalExecuteAt.isAfter(LocalDateTime.now()))
                            ? originalExecuteAt
                            : null;

                    return submitTask(
                            task.getTaskType(),
                            handlerRegistry.deserialize(task.getTaskType(), task.getRequestJson()),
                            userId,
                            task.getPriority(),
                            retryExecuteAt
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
        
        // 查找过期的已完成/失败任务。
        //
        // ⚠️ 过期判定已经下推到 SQL（findExpiredByStatus），
        // 这里<b>不能</b>再传 Pageable.unpaged()：Spring Data R2DBC 会把
        // `:#{#pageable.pageSize}` 求值成查询参数，而 Unpaged.getPageSize()
        // 直接抛 UnsupportedOperationException —— 整个查询在构造阶段就炸，
        // 表现为每晚的清理任务静默失败（日志里是 EL1021E: ... property 'pageSize'）。
        Flux<AsyncTask> expiredCompletedTasks =
                taskRepository.findExpiredByStatus(TaskStatus.COMPLETED, completedThreshold);

        Flux<AsyncTask> expiredFailedTasks =
                taskRepository.findExpiredByStatus(TaskStatus.FAILED, failedThreshold);

        // 合并并删除。
        // 用 concatMap（串行）而不是 flatMap（并发）：本方法不在事务里，
        // 但并发删除会同时占用连接池里的多条连接，容易把池打满；
        // 串行删除也便于按 submit_time 顺序稳定清理。
        return Flux.concat(expiredCompletedTasks, expiredFailedTasks)
                .concatMap(task -> {
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
