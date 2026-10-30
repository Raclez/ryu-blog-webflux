package com.ryu.blog.service.impl.article;

import com.ryu.blog.entity.Posts;
import com.ryu.blog.enums.PublishMode;
import com.ryu.blog.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发布方式 → 状态/发布时间的映射规则
 *
 * <p>这条规则直接决定「文章什么时候对外可见」：前台所有查询都硬编码
 * {@code status = 1}，所以 {@code status} 映射错了 = 文章该出现时没出现、
 * 或者不该出现时出现了。
 *
 * <p>规则是纯函数，所以不 mock 任何东西 —— 依赖越少，测试越不容易假失败。
 */
class ArticlePublishModeTest {

    @Test
    @DisplayName("DRAFT → 草稿，publish_time 置空")
    void draftClearsPublishTime() {
        Posts article = article();

        ArticlePublishPolicy.apply(article, PublishMode.DRAFT, null);

        assertThat(article.getStatus()).isEqualTo(Posts.Status.DRAFT);
        assertThat(article.getPublishTime()).isNull();
    }

    @Test
    @DisplayName("publishMode 为 null → 视为草稿（老客户端不带这个字段）")
    void nullModeIsTreatedAsDraft() {
        Posts article = article();
        article.setStatus(Posts.Status.PUBLISHED);
        article.setPublishTime(LocalDateTime.now());

        ArticlePublishPolicy.apply(article, null, null);

        assertThat(article.getStatus()).isEqualTo(Posts.Status.DRAFT);
        assertThat(article.getPublishTime()).isNull();
    }

    @Test
    @DisplayName("IMMEDIATE → 已发布，publish_time = 现在")
    void immediatePublishesNow() {
        Posts article = article();
        LocalDateTime before = LocalDateTime.now();

        ArticlePublishPolicy.apply(article, PublishMode.IMMEDIATE, null);

        assertThat(article.getStatus()).isEqualTo(Posts.Status.PUBLISHED);
        assertThat(article.getPublishTime()).isNotNull();
        assertThat(article.getPublishTime()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("IMMEDIATE 忽略 scheduleTime —— 立即发布就是立即，不看计划时间")
    void immediateIgnoresScheduleTime() {
        Posts article = article();

        ArticlePublishPolicy.apply(article, PublishMode.IMMEDIATE, LocalDateTime.now().plusDays(1));

        assertThat(article.getStatus()).isEqualTo(Posts.Status.PUBLISHED);
        assertThat(article.getPublishTime()).isBefore(LocalDateTime.now().plusMinutes(1));
    }

    @Test
    @DisplayName("IMMEDIATE 作用在已发布的文章上 → 不重置发布时间（改个错别字不该改掉首发日期）")
    void immediateKeepsPublishTimeOfPublishedArticle() {
        Posts article = article();
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(30);
        article.setStatus(Posts.Status.PUBLISHED);
        article.setPublishTime(publishedAt);

        ArticlePublishPolicy.apply(article, PublishMode.IMMEDIATE, null);

        assertThat(article.getStatus()).isEqualTo(Posts.Status.PUBLISHED);
        assertThat(article.getPublishTime()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("IMMEDIATE 作用在「待发布」的文章上 → 发布时间改成现在（原来的计划时间作废）")
    void immediateOverridesScheduledTime() {
        Posts article = article();
        article.setStatus(Posts.Status.PENDING);
        article.setPublishTime(LocalDateTime.now().plusHours(5));

        ArticlePublishPolicy.apply(article, PublishMode.IMMEDIATE, null);

        assertThat(article.getStatus()).isEqualTo(Posts.Status.PUBLISHED);
        assertThat(article.getPublishTime()).isBefore(LocalDateTime.now().plusMinutes(1));
    }

    @Test
    @DisplayName("SCHEDULED → 待发布，publish_time = 计划时间")
    void scheduledStaysPending() {
        Posts article = article();
        LocalDateTime scheduleTime = LocalDateTime.now().plusHours(2);

        ArticlePublishPolicy.apply(article, PublishMode.SCHEDULED, scheduleTime);

        assertThat(article.getStatus()).isEqualTo(Posts.Status.PENDING);
        assertThat(article.getPublishTime()).isEqualTo(scheduleTime);
    }

    @Test
    @DisplayName("SCHEDULED 缺少计划时间 → 报错")
    void scheduledWithoutTimeFails() {
        Posts article = article();

        assertThatThrownBy(() -> ArticlePublishPolicy.apply(article, PublishMode.SCHEDULED, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("定时发布时间不能为空");
    }

    @Test
    @DisplayName("SCHEDULED 的计划时间在过去 → 报错（不能把已经过点的文章「排到过去」）")
    void scheduledInThePastFails() {
        Posts article = article();

        assertThatThrownBy(() -> ArticlePublishPolicy.apply(article, PublishMode.SCHEDULED,
                LocalDateTime.now().minusMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须晚于当前时间");
    }

    @Test
    @DisplayName("SCHEDULED 的计划时间恰好是现在 → 也报错（必须严格晚于现在）")
    void scheduledExactlyNowFails() {
        Posts article = article();

        assertThatThrownBy(() -> ArticlePublishPolicy.apply(article, PublishMode.SCHEDULED, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("校验失败时文章状态不被改动 —— 不会留下一篇「半改」的脏数据")
    void failedValidationLeavesArticleUntouched() {
        Posts article = article();
        article.setStatus(Posts.Status.DRAFT);
        article.setPublishTime(null);

        assertThatThrownBy(() -> ArticlePublishPolicy.apply(article, PublishMode.SCHEDULED, null))
                .isInstanceOf(BusinessException.class);

        assertThat(article.getStatus()).isEqualTo(Posts.Status.DRAFT);
        assertThat(article.getPublishTime()).isNull();
    }

    private Posts article() {
        Posts posts = new Posts();
        posts.setId(1L);
        posts.setTitle("测试文章");
        return posts;
    }
}
