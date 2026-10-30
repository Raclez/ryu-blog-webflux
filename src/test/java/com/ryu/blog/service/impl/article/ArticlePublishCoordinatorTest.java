package com.ryu.blog.service.impl.article;

import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.enums.PublishMode;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.AsyncTaskRepository;
import com.ryu.blog.service.TaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文章发布编排
 *
 * <p>它守着两件事：
 * <ul>
 *   <li>定时发布 → 真的提交了一条 {@code ARTICLE_PUBLISH} 延迟任务；</li>
 *   <li>任务提交失败 → 文章退回草稿，<b>不留下「待发布但没有任务」的孤儿文章</b>。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ArticlePublishCoordinatorTest {

    private static final Long POST_ID = 42L;
    private static final Long AUTHOR_ID = 7L;

    @Mock
    private TaskService taskService;

    @Mock
    private AsyncTaskRepository taskRepository;

    @Mock
    private ArticleCrudService crudService;

    @InjectMocks
    private ArticlePublishCoordinator coordinator;

    // =====================================================================
    // 提交
    // =====================================================================

    @Test
    @DisplayName("定时发布 → 提交一条 ARTICLE_PUBLISH 延迟任务，计划时间就是文章的 publish_time")
    void submitsDelayedPublishTask() {
        LocalDateTime publishTime = LocalDateTime.now().plusHours(2);
        Posts article = pendingArticle(publishTime);

        when(taskRepository.findActiveByUserIdAndType(anyLong(), any(), any())).thenReturn(Flux.empty());
        when(taskService.submitTask(eq(TaskType.ARTICLE_PUBLISH), any(), eq(AUTHOR_ID),
                eq(TaskPriority.NORMAL), eq(publishTime)))
                .thenReturn(Mono.just(100L));

        StepVerifier.create(coordinator.afterWrite(article, PublishMode.SCHEDULED, publishTime, AUTHOR_ID))
                .expectNext(article)
                .verifyComplete();

        ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
        verify(taskService).submitTask(eq(TaskType.ARTICLE_PUBLISH), requestCaptor.capture(),
                eq(AUTHOR_ID), eq(TaskPriority.NORMAL), eq(publishTime));

        assertThat(requestCaptor.getValue()).isInstanceOf(com.ryu.blog.dto.ArticlePublishRequest.class);
        com.ryu.blog.dto.ArticlePublishRequest request =
                (com.ryu.blog.dto.ArticlePublishRequest) requestCaptor.getValue();
        assertThat(request.getPostId()).isEqualTo(POST_ID);
        assertThat(request.getPublishTime()).isEqualTo(publishTime);
    }

    @Test
    @DisplayName("不传发布方式 → 什么都不做（普通编辑不能把定时发布的任务撤掉）")
    void absentPublishModeTouchesNothing() {
        Posts article = pendingArticle(LocalDateTime.now().plusHours(2));

        StepVerifier.create(coordinator.afterWrite(article, null, null, AUTHOR_ID))
                .expectNext(article)
                .verifyComplete();

        verifyNoInteractions(taskService);
        verifyNoInteractions(taskRepository);
    }

    @Test
    @DisplayName("存草稿 → 撤掉旧任务，但不提交新任务")
    void draftCancelsOldTaskWithoutSubmittingNew() {
        Posts article = pendingArticle(LocalDateTime.now().plusHours(2));
        when(taskRepository.findActiveByUserIdAndType(anyLong(), any(), any()))
                .thenReturn(Flux.just(pendingTask(POST_ID)));
        when(taskService.cancelTask(anyLong(), anyLong())).thenReturn(Mono.just(true));

        StepVerifier.create(coordinator.afterWrite(article, PublishMode.DRAFT, null, AUTHOR_ID))
                .expectNext(article)
                .verifyComplete();

        verify(taskService).cancelTask(anyLong(), anyLong());
        verify(taskService, never()).submitTask(any(), any(), any(), any(), any());
    }

    // =====================================================================
    // 失败回滚
    // =====================================================================

    @Test
    @DisplayName("任务提交失败 → 文章退回草稿，错误继续向上抛")
    void revertsToDraftWhenTaskSubmissionFails() {
        LocalDateTime publishTime = LocalDateTime.now().plusHours(2);
        Posts article = pendingArticle(publishTime);

        when(taskRepository.findActiveByUserIdAndType(anyLong(), any(), any())).thenReturn(Flux.empty());
        when(taskService.submitTask(eq(TaskType.ARTICLE_PUBLISH), any(), eq(AUTHOR_ID),
                eq(TaskPriority.NORMAL), eq(publishTime)))
                .thenReturn(Mono.error(new BusinessException("Redis 连不上")));
        when(crudService.revertToDraft(POST_ID)).thenReturn(Mono.empty());

        StepVerifier.create(coordinator.afterWrite(article, PublishMode.SCHEDULED, publishTime, AUTHOR_ID))
                .expectError(BusinessException.class)
                .verify();

        verify(crudService).revertToDraft(POST_ID);
    }

    @Test
    @DisplayName("任务提交失败、且退回草稿也失败 → 错误仍然向上抛（不回滚成「成功」）")
    void stillFailsWhenRevertAlsoFails() {
        LocalDateTime publishTime = LocalDateTime.now().plusHours(2);
        Posts article = pendingArticle(publishTime);

        when(taskRepository.findActiveByUserIdAndType(anyLong(), any(), any())).thenReturn(Flux.empty());
        when(taskService.submitTask(eq(TaskType.ARTICLE_PUBLISH), any(), eq(AUTHOR_ID),
                eq(TaskPriority.NORMAL), eq(publishTime)))
                .thenReturn(Mono.error(new BusinessException("Redis 连不上")));
        when(crudService.revertToDraft(POST_ID))
                .thenReturn(Mono.error(new BusinessException("数据库也连不上")));

        StepVerifier.create(coordinator.afterWrite(article, PublishMode.SCHEDULED, publishTime, AUTHOR_ID))
                .expectError(BusinessException.class)
                .verify();
    }

    // =====================================================================
    // 撤销任务
    // =====================================================================

    @Test
    @DisplayName("撤销任务：只撤这篇文章自己的待执行任务")
    void cancelsOnlyOwnTasks() {
        Posts article = pendingArticle(LocalDateTime.now().plusHours(2));

        when(taskRepository.findActiveByUserIdAndType(anyLong(), any(), any()))
                .thenReturn(Flux.just(pendingTask(POST_ID), pendingTask(999L)));
        when(taskService.cancelTask(anyLong(), anyLong())).thenReturn(Mono.just(true));

        StepVerifier.create(coordinator.cancelPublishTask(article))
                .expectNext(1)
                .verifyComplete();

        verify(taskService).cancelTask(100L, AUTHOR_ID);
        verify(taskService, never()).cancelTask(101L, AUTHOR_ID);
    }

    @Test
    @DisplayName("撤销任务失败 → 不影响正确性（任务到点会自校验后跳过）")
    void survivesCancelFailure() {
        Posts article = pendingArticle(LocalDateTime.now().plusHours(2));

        when(taskRepository.findActiveByUserIdAndType(anyLong(), any(), any()))
                .thenReturn(Flux.just(pendingTask(POST_ID)));
        when(taskService.cancelTask(anyLong(), anyLong()))
                .thenReturn(Mono.error(new BusinessException("Redis 连不上")));

        StepVerifier.create(coordinator.cancelPublishTask(article))
                .expectNext(0)
                .verifyComplete();
    }

    // =====================================================================
    // 辅助
    // =====================================================================

    private Posts pendingArticle(LocalDateTime publishTime) {
        Posts article = new Posts();
        article.setId(POST_ID);
        article.setTitle("测试文章");
        article.setUserId(AUTHOR_ID);
        article.setStatus(Posts.Status.PENDING);
        article.setPublishTime(publishTime);
        article.setIsDeleted(false);
        return article;
    }

    /**
     * 一条待执行的发布任务。{@code id} 决定取消时被调用的 taskId。
     */
    private AsyncTask pendingTask(Long postId) {
        AsyncTask task = new AsyncTask();
        task.setId(postId == null ? null : 100L + (postId == POST_ID ? 0 : 1));
        task.setUserId(AUTHOR_ID);
        task.setTaskType(TaskType.ARTICLE_PUBLISH);
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.NORMAL);
        task.setRequestJson("{\"postId\":" + postId + ",\"publishTime\":\"2026-10-29T20:00:00\"}");
        return task;
    }
}
