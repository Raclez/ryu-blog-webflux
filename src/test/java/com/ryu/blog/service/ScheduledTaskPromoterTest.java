package com.ryu.blog.service;

import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.repository.AsyncTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 延迟任务提升器
 *
 * <p>它守的是「定时发布会不会真的到点执行」：
 * <ul>
 *   <li>到期任务要被搬进主队列（code=1：从延迟区搬出来的）；</li>
 *   <li>Redis 丢过数据、任务不在任何队列里时，要能<b>重建</b>（code=2）；</li>
 *   <li>已经在队列/处理中时不要重复搬（code=0）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ScheduledTaskPromoterTest {

    private static final int BATCH_SIZE = 10;

    @Mock
    private AsyncTaskRepository taskRepository;

    @Mock
    private TaskQueueManager queueManager;

    private ScheduledTaskPromoter promoter;

    @BeforeEach
    void setUp() {
        promoter = new ScheduledTaskPromoter(taskRepository, queueManager);
        // @Value 不会在纯单元测试里注入，批次大小必须显式设，
        // 否则是 0 —— 查不到任何任务，测试会「假绿」。
        promoter.setBatchSize(BATCH_SIZE);
    }

    @Test
    @DisplayName("到期的任务被搬进主队列")
    void promotesDueTask() {
        AsyncTask task = dueTask(100L);
        when(taskRepository.findDuePendingTasks(eq(TaskStatus.PENDING), any(LocalDateTime.class), anyInt()))
                .thenReturn(Flux.just(task));
        when(queueManager.promoteToQueue(anyString(), any(), any()))
                .thenReturn(Mono.just(1));

        StepVerifier.create(promoter.promoteDueTasksNow())
                .expectNext(1L)
                .verifyComplete();

        verify(queueManager).promoteToQueue(eq("100"), eq(TaskPriority.NORMAL), eq(task.getExecuteAt()));
    }

    @Test
    @DisplayName("Redis 丢过数据（任务不在队列里）→ 重建入队，也算提升成功")
    void rebuildsTaskLostFromRedis() {
        AsyncTask task = dueTask(101L);
        when(taskRepository.findDuePendingTasks(eq(TaskStatus.PENDING), any(LocalDateTime.class), anyInt()))
                .thenReturn(Flux.just(task));
        // Lua 返回 2 = 「不在延迟区、也不在队列/处理中」→ 直接补进队列
        when(queueManager.promoteToQueue(anyString(), any(), any()))
                .thenReturn(Mono.just(2));

        StepVerifier.create(promoter.promoteDueTasksNow())
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    @DisplayName("任务已经在队列/处理中 → 不重复搬，也不算提升")
    void doesNotDuplicateAlreadyQueuedTask() {
        AsyncTask task = dueTask(102L);
        when(taskRepository.findDuePendingTasks(eq(TaskStatus.PENDING), any(LocalDateTime.class), anyInt()))
                .thenReturn(Flux.just(task));
        when(queueManager.promoteToQueue(anyString(), any(), any()))
                .thenReturn(Mono.just(0));

        StepVerifier.create(promoter.promoteDueTasksNow())
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("没有任务ID的行 → 直接跳过，不去碰队列")
    void skipsTaskWithoutId() {
        AsyncTask task = dueTask(103L);
        task.setId(null);
        when(taskRepository.findDuePendingTasks(eq(TaskStatus.PENDING), any(LocalDateTime.class), anyInt()))
                .thenReturn(Flux.just(task));

        StepVerifier.create(promoter.promoteDueTasksNow())
                .expectNext(0L)
                .verifyComplete();

        verify(queueManager, never()).promoteToQueue(anyString(), any(), any());
    }

    @Test
    @DisplayName("execute_at 为空 → 跳过（查询条件本该排除它，多一层更便宜）")
    void skipsTaskWithoutExecuteAt() {
        AsyncTask task = dueTask(104L);
        task.setExecuteAt(null);
        when(taskRepository.findDuePendingTasks(eq(TaskStatus.PENDING), any(LocalDateTime.class), anyInt()))
                .thenReturn(Flux.just(task));

        StepVerifier.create(promoter.promoteDueTasksNow())
                .expectNext(0L)
                .verifyComplete();

        verify(queueManager, never()).promoteToQueue(anyString(), any(), any());
    }

    @Test
    @DisplayName("Redis 出错 → 不冒泡，剩下的任务继续搬")
    void survivesRedisFailure() {
        AsyncTask broken = dueTask(105L);
        AsyncTask healthy = dueTask(106L);
        when(taskRepository.findDuePendingTasks(eq(TaskStatus.PENDING), any(LocalDateTime.class), anyInt()))
                .thenReturn(Flux.just(broken, healthy));
        when(queueManager.promoteToQueue(eq("105"), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("Redis 连不上")));
        when(queueManager.promoteToQueue(eq("106"), any(), any()))
                .thenReturn(Mono.just(1));

        StepVerifier.create(promoter.promoteDueTasksNow())
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    @DisplayName("查库用的批次大小就是配置值")
    void usesConfiguredBatchSize() {
        when(taskRepository.findDuePendingTasks(eq(TaskStatus.PENDING), any(LocalDateTime.class), anyInt()))
                .thenReturn(Flux.empty());

        promoter.promoteDueTasksNow().block();

        verify(taskRepository).findDuePendingTasks(eq(TaskStatus.PENDING), any(LocalDateTime.class), eq(BATCH_SIZE));
    }

    private AsyncTask dueTask(Long id) {
        AsyncTask task = new AsyncTask();
        task.setId(id);
        task.setUserId(1L);
        task.setTaskType(TaskType.ARTICLE_PUBLISH);
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.NORMAL);
        task.setSubmitTime(LocalDateTime.now().minusMinutes(5));
        task.setExecuteAt(LocalDateTime.now().minusMinutes(1));
        return task;
    }
}
