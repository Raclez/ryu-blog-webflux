package com.ryu.blog.service;

import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.repository.AsyncTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 延迟任务提升器
 *
 * <p>把到期的延迟任务从延迟区搬进待执行队列。
 *
 * <p><b>为什么需要它</b>：主队列的出队脚本（{@code TaskQueueManager#DEQUEUE_SCRIPT}）
 * 只按 score 排序取头部，<b>没有任何到期闸门</b> —— 进了主队列的任务下一轮就会被执行。
 * 主队列的 score 里高位是优先级、低位是时间，表达的是「谁先执行」而不是「什么时候执行」。
 * 所以延迟任务必须待在另一个 key（{@code task:scheduled:all}）里，由本类到期搬运。
 *
 * <p><b>DB 是到期的唯一权威</b>：本类每次都从 {@code t_async_tasks} 里查
 * {@code status=PENDING AND execute_at <= now} 的行，而不是从 Redis 的延迟区里查。
 * 这样 Redis 被清空、延迟区丢失之后，任务依然能被捞回来重新入队。
 *
 * @author ryu
 */
@Slf4j
@Component
public class ScheduledTaskPromoter {

    private final AsyncTaskRepository taskRepository;
    private final TaskQueueManager queueManager;

    /** 单次最多提升多少个任务，避免一次拉太多把队列打爆。 */
    @Value("${task.queue.scheduled-promote-batch-size:100}")
    private int batchSize;

    public ScheduledTaskPromoter(AsyncTaskRepository taskRepository, TaskQueueManager queueManager) {
        this.taskRepository = taskRepository;
        this.queueManager = queueManager;
    }

    /**
     * 定时提升到期任务
     */
    @Scheduled(fixedDelayString = "${task.queue.scheduled-promote-interval-ms:3000}")
    public void promoteDueTasks() {
        LocalDateTime now = LocalDateTime.now();

        taskRepository.findDuePendingTasks(TaskStatus.PENDING, now, batchSize)
                .concatMap(task -> promote(task, now))
                .subscribe(
                        promoted -> {
                            // 单个任务的结果已经各自记录日志，这里只统计总量
                        },
                        error -> log.error("Failed to promote due tasks: {}", error.getMessage(), error),
                        () -> log.debug("Scheduled task promotion cycle finished at {}", now)
                );
    }

    /**
     * 提升单个任务
     *
     * @return 是否真的被搬进了待执行队列
     */
    private Mono<Boolean> promote(AsyncTask task, LocalDateTime now) {
        String taskId = task.getTaskId();
        if (taskId == null) {
            return Mono.just(false);
        }

        // execute_at 为 null 的任务不该出现在这里（查询条件排除了 null），
        // 但实体是别的代码写的，多一层判断比线上出现一个 score=NaN 的任务便宜。
        LocalDateTime executeAt = task.getExecuteAt();
        if (executeAt == null) {
            return Mono.just(false);
        }

        return queueManager.promoteToQueue(taskId, task.getPriority(), executeAt)
                .map(code -> {
                    if (code > 0) {
                        log.info("Promoted due task: taskId={}, type={}, executeAt={}, code={}",
                                taskId, task.getTaskType(), executeAt, code);
                    }
                    return code > 0;
                })
                .onErrorResume(error -> {
                    log.error("Failed to promote task {}: {}", taskId, error.getMessage(), error);
                    return Mono.just(false);
                });
    }

    /**
     * 提升到期任务（供测试与手工触发使用）
     *
     * @return 被提升的任务数
     */
    public Mono<Long> promoteDueTasksNow() {
        LocalDateTime now = LocalDateTime.now();
        return taskRepository.findDuePendingTasks(TaskStatus.PENDING, now, batchSize)
                .concatMap(task -> promote(task, now))
                // 只数「真的搬进队列了」的：
                // promote 对跳过（无 taskId / 无 execute_at / 已在队列）也会 emit 一个 false，
                // 不过滤的话计数会把这些也算成「提升成功」。
                .filter(Boolean.TRUE::equals)
                .count();
    }

    /**
     * 供测试注入固定批次大小
     */
    void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * 暴露 Flux 形态，便于测试断言
     */
    Flux<AsyncTask> findDue(LocalDateTime now) {
        return taskRepository.findDuePendingTasks(TaskStatus.PENDING, now, batchSize);
    }
}
