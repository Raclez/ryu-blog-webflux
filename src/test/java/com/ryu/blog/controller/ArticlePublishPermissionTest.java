package com.ryu.blog.controller;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.enums.PublishMode;
import com.ryu.blog.service.ArticleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「发布」这一动作的能力校验
 *
 * <p><b>为什么要有这条</b>：创建文章的端点只要求 {@code content:post:add}，
 * 而发布要走 {@code PUT /posts/status}（要求 {@code content:post:publish}）。
 * 如果创建时能直接选「立即发布 / 定时发布」，等于给发布闸门开了个后门 ——
 * 当初 DTO 里的 {@code status} 字段就是这么被绕过去的（见 {@code PostUpdateDTO} 的注释）。
 *
 * <p>注解上的 {@code @SaCheckPermission} 是静态的，「按发布方式决定要不要发布能力」
 * 只能在方法体里自己判。
 */
@ExtendWith(MockitoExtension.class)
class ArticlePublishPermissionTest {

    @Mock
    private ArticleService articleService;

    private PostsController controller() {
        return new PostsController(articleService);
    }

    // =====================================================================
    // 需要发布能力的两种方式
    // =====================================================================

    @Test
    @DisplayName("创建时选「立即发布」→ 必须有 content:post:publish")
    void immediatePublishRequiresPublishPermission() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdAsLong()).thenReturn(1L);
            stpUtil.when(() -> StpUtil.checkPermission(PermissionConstants.POST_PUBLISH))
                    .thenThrow(new NotPermissionException(PermissionConstants.POST_PUBLISH));

            assertThatThrownBy(() -> controller().createPost(createDto(PublishMode.IMMEDIATE)))
                    .isInstanceOf(NotPermissionException.class);
        }

        verify(articleService, never()).createArticle(any(), any());
    }

    @Test
    @DisplayName("创建时选「定时发布」→ 必须有 content:post:publish")
    void scheduledPublishRequiresPublishPermission() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdAsLong()).thenReturn(1L);
            stpUtil.when(() -> StpUtil.checkPermission(PermissionConstants.POST_PUBLISH))
                    .thenThrow(new NotPermissionException(PermissionConstants.POST_PUBLISH));

            assertThatThrownBy(() -> controller().createPost(createDto(PublishMode.SCHEDULED)))
                    .isInstanceOf(NotPermissionException.class);
        }

        verify(articleService, never()).createArticle(any(), any());
    }

    @Test
    @DisplayName("更新时改发布方式 → 同样要校验发布能力")
    void updateWithPublishModeRequiresPublishPermission() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.checkPermission(PermissionConstants.POST_PUBLISH))
                    .thenThrow(new NotPermissionException(PermissionConstants.POST_PUBLISH));

            PostUpdateDTO dto = new PostUpdateDTO();
            dto.setId(1L);
            dto.setTitle("标题");
            dto.setContent("正文");
            dto.setCategoryId(1L);
            dto.setPublishMode(PublishMode.IMMEDIATE);

            assertThatThrownBy(() -> controller().updatePost(dto))
                    .isInstanceOf(NotPermissionException.class);
        }
    }

    // =====================================================================
    // 不需要发布能力的两种情况
    // =====================================================================

    @Test
    @DisplayName("存草稿 → 不需要发布能力，正常创建")
    void draftDoesNotRequirePublishPermission() {
        when(articleService.createArticle(any(), any())).thenReturn(Mono.just(new Posts()));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdAsLong()).thenReturn(1L);
            // checkPermission 一旦被调用就抛 —— 没被调用才说明这条请求不需要发布能力
            stpUtil.when(() -> StpUtil.checkPermission(PermissionConstants.POST_PUBLISH))
                    .thenThrow(new AssertionError("存草稿不该校验发布能力"));

            controller().createPost(createDto(PublishMode.DRAFT)).block();
        }

        verify(articleService).createArticle(any(), any());
    }

    @Test
    @DisplayName("不传发布方式（老客户端）→ 视为草稿，不校验发布能力")
    void absentPublishModeDoesNotRequirePublishPermission() {
        when(articleService.createArticle(any(), any())).thenReturn(Mono.just(new Posts()));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdAsLong()).thenReturn(1L);
            stpUtil.when(() -> StpUtil.checkPermission(PermissionConstants.POST_PUBLISH))
                    .thenThrow(new AssertionError("不传发布方式不该校验发布能力"));

            controller().createPost(createDto(null)).block();
        }

        verify(articleService).createArticle(any(), any());
    }

    @Test
    @DisplayName("普通编辑（不传发布方式）→ 不校验发布能力")
    void plainEditDoesNotRequirePublishPermission() {
        when(articleService.updateArticle(any())).thenReturn(Mono.just(new Posts()));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.checkPermission(PermissionConstants.POST_PUBLISH))
                    .thenThrow(new AssertionError("普通编辑不该校验发布能力"));
            // OperatorContext.current() 会问登录态，这里让它走「已登录」
            stpUtil.when(StpUtil::isLogin).thenReturn(true);
            stpUtil.when(() -> StpUtil.getLoginIdAsLong()).thenReturn(1L);
            stpUtil.when(() -> StpUtil.hasPermission(anyString())).thenReturn(false);

            PostUpdateDTO dto = new PostUpdateDTO();
            dto.setId(1L);
            dto.setTitle("标题");
            dto.setContent("正文");
            dto.setCategoryId(1L);

            controller().updatePost(dto).block();
        }

        verify(articleService).updateArticle(any());
    }

    @Test
    @DisplayName("有发布能力时，创建文章正常通过")
    void publishWithPermissionPasses() {
        when(articleService.createArticle(any(), any())).thenReturn(Mono.just(new Posts()));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdAsLong()).thenReturn(1L);
            // checkPermission 不抛 = 有权限

            assertThat(controller().createPost(createDto(PublishMode.IMMEDIATE)).block()).isNotNull();
        }

        verify(articleService).createArticle(any(), any());
    }

    // =====================================================================
    // 辅助
    // =====================================================================

    private PostCreateDTO createDto(PublishMode mode) {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle("标题");
        dto.setContent("正文");
        dto.setCategoryId(1L);
        dto.setPublishMode(mode);
        if (mode == PublishMode.SCHEDULED) {
            dto.setScheduleTime(LocalDateTime.now().plusHours(1));
        }
        return dto;
    }
}
