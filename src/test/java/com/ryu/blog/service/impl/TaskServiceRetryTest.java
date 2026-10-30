package com.ryu.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.AsyncTaskRepository;
import com.ryu.blog.service.TaskHandlerRegistry;
import com.ryu.blog.service.TaskNotificationService;
import com.ryu.blog.service.TaskQueueManager;
import com.ryu.blog.service.TaskQuotaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 重试任务时的计划执行时间
 *
 * <p><b>为什么这值得测</b>：重试原来是把参数原样复制一份再提交，
 * <b>唯独丢了 {@code execute_at}</b> —— 而 {@code execute_at} 决定任务进主队列还是延迟区。
 * 丢了它的后果是：一条「明早 8 点发布」的定时任务一旦失败，
 * 点重试会<b>立刻</b>把文章放出来 —— 定时发布的意义就没了。
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceRetryTest {

    private static final Long USER_ID = 7L;
    private static final Long TASK_ID = 100L;

    @Mock
    private AsyncTaskRepository taskRepository;

    @Mock
    private TaskQueueManager queueManager;

    @Mock
    private TaskQuotaService quotaService;

    @Mock
    private TaskNotificationService notificationService;

    @Mock
    private TaskHandlerRegistry handlerRegistry;

    private TaskServiceImpl taskService() {
        return new TaskServiceImpl(taskRepository, queueManager, quotaService,
                notificationService, handlerRegistry);
    }

    // =====================================================================
    // 还没到点的任务
    // =====================================================================

    @Test
    @DisplayName("重试一条还没到点的定时任务 → 保留原来的计划时间，不提前执行")
    void keepsFutureExecuteAt() {
        LocalDateTime future = LocalDateTime.now().plusHours(6);
        AsyncTask failed = failedTask(future);

        when(taskRepository.findById(TASK_ID)).thenReturn(Mono.just(failed));
        when(handlerRegistry.deserialize(eq(TaskType.ARTICLE_PUBLISH), any()))
                .thenReturn(Map.of("postId", 42L));
        when(quotaService.checkAndConsumeQuota(anyLong(), any())).thenReturn(Mono.empty());
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> {
            AsyncTask saved = inv.getArgument(0);
            saved.setId(999L);
            return Mono.just(saved);
        });
        when(queueManager.enqueueScheduled(any(), any())).thenReturn(Mono.empty());

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            StepVerifier.create(taskService().retryTask(TASK_ID))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        // 关键断言：进的是延迟区，计划时间没丢
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(queueManager).enqueueScheduled(any(), timeCaptor.capture());
        assertThat(timeCaptor.getValue()).isEqualTo(future);

        // 而且没进主队列
        verify(queueManager, never()).enqueue(any(), any(), any());
    }

    // =====================================================================
    // 已经过了点的任务
    // =====================================================================

    @Test
    @DisplayName("重试一条已经过了点的任务 → 直接进主队列立即执行，不进延迟区")
    void pastExecuteAtRunsImmediately() {
        AsyncTask failed = failedTask(LocalDateTime.now().minusHours(6));

        when(taskRepository.findById(TASK_ID)).thenReturn(Mono.just(failed));
        when(handlerRegistry.deserialize(eq(TaskType.ARTICLE_PUBLISH), any()))
                .thenReturn(Map.of("postId", 42L));
        when(quotaService.checkAndConsumeQuota(anyLong(), any())).thenReturn(Mono.empty());
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> {
            AsyncTask saved = inv.getArgument(0);
            saved.setId(999L);
            return Mono.just(saved);
        });
        when(queueManager.enqueue(any(), any(), any())).thenReturn(Mono.empty());

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            StepVerifier.create(taskService().retryTask(TASK_ID))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        verify(queueManager).enqueue(any(), any(), any());
        verify(queueManager, never()).enqueueScheduled(any(), any());
    }

    @Test
    @DisplayName("重试一条没有计划时间的任务（普通任务）→ 立即执行")
    void immediateTaskStaysImmediate() {
        AsyncTask failed = failedTask(null);

        when(taskRepository.findById(TASK_ID)).thenReturn(Mono.just(failed));
        when(handlerRegistry.deserialize(eq(TaskType.ARTICLE_PUBLISH), any()))
                .thenReturn(Map.of("postId", 42L));
        when(quotaService.checkAndConsumeQuota(anyLong(), any())).thenReturn(Mono.empty());
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> {
            AsyncTask saved = inv.getArgument(0);
            saved.setId(999L);
            return Mono.just(saved);
        });
        when(queueManager.enqueue(any(), any(), any())).thenReturn(Mono.empty());

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            StepVerifier.create(taskService().retryTask(TASK_ID))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        verify(queueManager).enqueue(any(), any(), any());
        verify(queueManager, never()).enqueueScheduled(any(), any());
    }

    // =====================================================================
    // 非失败任务不许重试
    // =====================================================================

    @Test
    @DisplayName("重试一条没失败的任务 → 报错，不提交新任务")
    void cannotRetryNonFailedTask() {
        AsyncTask pending = failedTask(LocalDateTime.now().plusHours(1));
        pending.setStatus(TaskStatus.PENDING);

        when(taskRepository.findById(TASK_ID)).thenReturn(Mono.just(pending));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            StepVerifier.create(taskService().retryTask(TASK_ID))
                    .expectError(BusinessException.class)
                    .verify();
        }

        verify(taskRepository, never()).save(any(AsyncTask.class));
    }

    // =====================================================================
    // 辅助
    // =====================================================================

    private AsyncTask failedTask(LocalDateTime executeAt) {
        AsyncTask task = new AsyncTask();
        task.setId(TASK_ID);
        task.setUserId(USER_ID);
        task.setTaskType(TaskType.ARTICLE_PUBLISH);
        task.setStatus(TaskStatus.FAILED);
        task.setPriority(TaskPriority.NORMAL);
        task.setExecuteAt(executeAt);
        task.setIsDeleted(false);
        task.setRequestJson("{\"postId\":42}");
        task.setRetryCount(1);
        task.setMaxRetries(3);
        return task;
    }
}
