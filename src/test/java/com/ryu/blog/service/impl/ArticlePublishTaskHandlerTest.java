package com.ryu.blog.service.impl;

import com.ryu.blog.dto.ArticlePublishRequest;
import com.ryu.blog.dto.ArticlePublishResult;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.service.ArticleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.ryu.blog.utils.OperatorContexts;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定时发布任务处理器
 *
 * <p>这个 handler 是「文章自动上架」的唯一执行点，它的行为必须精确：
 * <ul>
 *   <li>到点且文章仍是「待发布」→ <b>走门面</b>发布（门面才有缓存失效注解）；</li>
 *   <li>文章已经被手动发布 / 已取消 / 已删除 / 还没到点 → <b>跳过</b>，不算失败。</li>
 * </ul>
 *
 * <p>「跳过」与「失败」必须分开：跳过的任务会被记成 COMPLETED，
 * 而失败会进重试队列 —— 把「本来就无事可做」当成失败，
 * 会让任务列表里堆满红色。
 */
@ExtendWith(MockitoExtension.class)
class ArticlePublishTaskHandlerTest {

    private static final Long POST_ID = 42L;
    private static final Long AUTHOR_ID = 7L;

    @Mock
    private ArticleService articleService;

    @Mock
    private PostsRepository postsRepository;

    @InjectMocks
    private ArticlePublishTaskHandler handler;

    /**
     * 门面被调用时，从 Reactor Context 里读到的操作者身份。
     *
     * <p>身份不再走方法参数 —— handler 把它写进 Reactor Context，
     * Service 层从 Context 取。所以要断言「以谁的身份发布」，
     * 只能在门面这一侧把 Context 里的身份读出来。
     */
    private final AtomicReference<OperatorContext> seenOperator = new AtomicReference<>();

    // =====================================================================
    // 正常路径
    // =====================================================================

    @Test
    @DisplayName("待发布且已到点 → 走门面发布，并返回 published")
    void publishesWhenDue() {
        Posts article = pendingArticle();
        when(postsRepository.findById(POST_ID)).thenReturn(Mono.just(article));
        when(articleService.updateArticleStatus(any()))
                .thenReturn(Mono.just(published(article)));

        StepVerifier.create(handler.execute(request(LocalDateTime.now().minusMinutes(1))))
                .assertNext(result -> {
                    assertThat(result.isPublished()).isTrue();
                    assertThat(result.isSkipped()).isFalse();
                    assertThat(result.getPostId()).isEqualTo(POST_ID);
                    assertThat(result.getTitle()).isEqualTo("测试文章");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("发布时调的是门面 ArticleService，且状态是「已发布」")
    void callsFacadeWithPublishedStatus() {
        Posts article = pendingArticle();
        when(postsRepository.findById(POST_ID)).thenReturn(Mono.just(article));
        when(articleService.updateArticleStatus(any()))
                .thenReturn(Mono.deferContextual(cv -> {
                    seenOperator.set(OperatorContexts.from(cv));
                    return Mono.just(published(article));
                }));

        handler.execute(request(LocalDateTime.now().minusMinutes(1))).block();

        ArgumentCaptor<PostStatusDTO> statusCaptor = ArgumentCaptor.forClass(PostStatusDTO.class);
        verify(articleService).updateArticleStatus(statusCaptor.capture());

        assertThat(statusCaptor.getValue().getId()).isEqualTo(POST_ID);
        assertThat(statusCaptor.getValue().getStatus()).isEqualTo(Posts.Status.PUBLISHED);
        // 以「作者本人」的身份发布 —— 归属校验靠这个通过，不做提权。
        // 身份不再走参数，而是由 handler 写进 Reactor Context，
        // Service 层从 Context 取 —— 所以这里看 Service 看到的那个 Context。
        assertThat(seenOperator.get().userId()).isEqualTo(AUTHOR_ID);
        assertThat(seenOperator.get().canManageOthers()).isFalse();
    }

    // =====================================================================
    // 自校验：跳过，而不是失败
    // =====================================================================

    @Test
    @DisplayName("文章不存在 → 跳过，不调门面")
    void skipsWhenArticleMissing() {
        when(postsRepository.findById(POST_ID)).thenReturn(Mono.empty());

        StepVerifier.create(handler.execute(request(LocalDateTime.now().minusMinutes(1))))
                .assertNext(result -> {
                    assertThat(result.isSkipped()).isTrue();
                    assertThat(result.isPublished()).isFalse();
                    assertThat(result.getReason()).contains("文章不存在");
                })
                .verifyComplete();

        verifyNoInteractions(articleService);
    }

    @Test
    @DisplayName("文章已删除 → 跳过")
    void skipsWhenArticleDeleted() {
        Posts article = pendingArticle();
        article.setIsDeleted(true);
        when(postsRepository.findById(POST_ID)).thenReturn(Mono.just(article));

        StepVerifier.create(handler.execute(request(LocalDateTime.now().minusMinutes(1))))
                .assertNext(result -> {
                    assertThat(result.isSkipped()).isTrue();
                    assertThat(result.getReason()).contains("已删除");
                })
                .verifyComplete();

        verifyNoInteractions(articleService);
    }

    @Test
    @DisplayName("文章状态已被改成「已发布」→ 跳过（幂等：不会重复发布）")
    void skipsWhenAlreadyPublished() {
        Posts article = pendingArticle();
        article.setStatus(Posts.Status.PUBLISHED);
        when(postsRepository.findById(POST_ID)).thenReturn(Mono.just(article));

        StepVerifier.create(handler.execute(request(LocalDateTime.now().minusMinutes(1))))
                .assertNext(result -> {
                    assertThat(result.isSkipped()).isTrue();
                    assertThat(result.getReason()).contains("状态已变更");
                })
                .verifyComplete();

        verifyNoInteractions(articleService);
    }

    @Test
    @DisplayName("文章已退回草稿 → 跳过（取消定时发布后，旧任务到点无事可做）")
    void skipsWhenRevertedToDraft() {
        Posts article = pendingArticle();
        article.setStatus(Posts.Status.DRAFT);
        when(postsRepository.findById(POST_ID)).thenReturn(Mono.just(article));

        StepVerifier.create(handler.execute(request(LocalDateTime.now().minusMinutes(1))))
                .assertNext(result -> assertThat(result.isSkipped()).isTrue())
                .verifyComplete();

        verifyNoInteractions(articleService);
    }

    @Test
    @DisplayName("publish_time 仍在未来 → 跳过（提前到点：再等等）")
    void skipsWhenStillInFuture() {
        Posts article = pendingArticle();
        article.setPublishTime(LocalDateTime.now().plusMinutes(10));
        when(postsRepository.findById(POST_ID)).thenReturn(Mono.just(article));

        StepVerifier.create(handler.execute(request(LocalDateTime.now().minusMinutes(1))))
                .assertNext(result -> {
                    assertThat(result.isSkipped()).isTrue();
                    assertThat(result.getReason()).contains("还未到发布时间");
                })
                .verifyComplete();

        verifyNoInteractions(articleService);
    }

    // =====================================================================
    // 真正的失败要冒泡（任务会进重试队列）
    // =====================================================================

    @Test
    @DisplayName("发布过程抛异常 → 错误向上冒泡（不是跳过）")
    void propagatesRealFailure() {
        Posts article = pendingArticle();
        when(postsRepository.findById(POST_ID)).thenReturn(Mono.just(article));
        when(articleService.updateArticleStatus(any()))
                .thenReturn(Mono.error(new IllegalStateException("数据库挂了")));

        StepVerifier.create(handler.execute(request(LocalDateTime.now().minusMinutes(1))))
                .expectErrorMessage("数据库挂了")
                .verify();
    }

    // =====================================================================
    // 通知文案
    // =====================================================================

    @Test
    @DisplayName("成功文案带上文章标题")
    void successMessageContainsTitle() {
        String message = handler.successMessage(ArticlePublishResult.published(POST_ID, "测试文章"));

        assertThat(message).contains("测试文章");
    }

    @Test
    @DisplayName("跳过的文案说明原因")
    void skippedMessageContainsReason() {
        String message = handler.successMessage(ArticlePublishResult.skipped(POST_ID, "测试文章", "文章状态已变更"));

        assertThat(message).contains("跳过");
        assertThat(message).contains("文章状态已变更");
    }

    @Test
    @DisplayName("失败文案带上异常信息")
    void failureMessageContainsError() {
        String message = handler.failureMessage(new IllegalStateException("数据库挂了"));

        assertThat(message).contains("数据库挂了");
    }

    // =====================================================================
    // 辅助
    // =====================================================================

    private ArticlePublishRequest request(LocalDateTime publishTime) {
        return ArticlePublishRequest.builder()
                .postId(POST_ID)
                .publishTime(publishTime)
                .build();
    }

    private Posts pendingArticle() {
        Posts article = new Posts();
        article.setId(POST_ID);
        article.setTitle("测试文章");
        article.setUserId(AUTHOR_ID);
        article.setStatus(Posts.Status.PENDING);
        article.setPublishTime(LocalDateTime.now().minusMinutes(1));
        article.setIsDeleted(false);
        return article;
    }

    private Posts published(Posts source) {
        Posts article = new Posts();
        article.setId(source.getId());
        article.setTitle(source.getTitle());
        article.setUserId(source.getUserId());
        article.setStatus(Posts.Status.PUBLISHED);
        article.setPublishTime(source.getPublishTime());
        article.setIsDeleted(false);
        return article;
    }
}
