package com.ryu.blog.service.impl.article;

import com.ryu.blog.entity.Posts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章可见性规则的单元测试
 *
 * <p>这条规则是安全边界：以前 {@code GET /posts/detail/{id}} 只要不是已删除就返回，
 * 草稿、待发布、已归档、private 全都能靠枚举 id 读到全文。
 *
 * <p>规则本身是纯函数，所以不 mock 任何东西 —— 依赖越少，测试越不容易假失败。
 */
class ArticleVisibilityPolicyTest {

    private static final Long AUTHOR_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;

    @Test
    @DisplayName("已发布且公开 → 任何人都能读")
    void publishedAndPublicIsVisibleToEveryone() {
        Posts article = article(Posts.Status.PUBLISHED, Posts.Visibility.PUBLIC, AUTHOR_ID);

        assertTrue(ArticleVisibilityPolicy.canView(article, null));
        assertTrue(ArticleVisibilityPolicy.canView(article, OTHER_USER_ID));
        assertTrue(ArticleVisibilityPolicy.isPubliclyVisible(article));
    }

    @Test
    @DisplayName("草稿 → 匿名和其他用户都读不到，只有作者能读")
    void draftIsOnlyVisibleToAuthor() {
        Posts article = article(Posts.Status.DRAFT, Posts.Visibility.PUBLIC, AUTHOR_ID);

        assertFalse(ArticleVisibilityPolicy.canView(article, null));
        assertFalse(ArticleVisibilityPolicy.canView(article, OTHER_USER_ID));
        assertTrue(ArticleVisibilityPolicy.canView(article, AUTHOR_ID));
        assertFalse(ArticleVisibilityPolicy.isPubliclyVisible(article));
    }

    @Test
    @DisplayName("待发布 / 已归档 → 同样只有作者能读")
    void pendingAndArchivedAreOnlyVisibleToAuthor() {
        for (int status : new int[]{Posts.Status.PENDING, Posts.Status.ARCHIVED}) {
            Posts article = article(status, Posts.Visibility.PUBLIC, AUTHOR_ID);

            assertFalse(ArticleVisibilityPolicy.canView(article, null), "status=" + status);
            assertFalse(ArticleVisibilityPolicy.canView(article, OTHER_USER_ID), "status=" + status);
            assertTrue(ArticleVisibilityPolicy.canView(article, AUTHOR_ID), "status=" + status);
        }
    }

    @Test
    @DisplayName("已发布但 visibility=private → 匿名读不到")
    void publishedButPrivateIsNotPubliclyVisible() {
        Posts article = article(Posts.Status.PUBLISHED, Posts.Visibility.PRIVATE, AUTHOR_ID);

        assertFalse(ArticleVisibilityPolicy.canView(article, null));
        assertFalse(ArticleVisibilityPolicy.canView(article, OTHER_USER_ID));
        assertTrue(ArticleVisibilityPolicy.canView(article, AUTHOR_ID));
        assertFalse(ArticleVisibilityPolicy.isPubliclyVisible(article));
    }

    @Test
    @DisplayName("visibility 为 null（历史数据）按公开处理，但状态仍要已发布")
    void nullVisibilityTreatedAsPublic() {
        Posts article = article(Posts.Status.PUBLISHED, null, AUTHOR_ID);

        assertTrue(ArticleVisibilityPolicy.canView(article, null));

        Posts draft = article(Posts.Status.DRAFT, null, AUTHOR_ID);
        assertFalse(ArticleVisibilityPolicy.canView(draft, null));
        assertTrue(ArticleVisibilityPolicy.canView(draft, AUTHOR_ID));
    }

    @Test
    @DisplayName("状态为 null 视为未发布")
    void nullStatusTreatedAsNotPublished() {
        Posts article = article(null, Posts.Visibility.PUBLIC, AUTHOR_ID);

        assertFalse(ArticleVisibilityPolicy.canView(article, null));
        assertTrue(ArticleVisibilityPolicy.canView(article, AUTHOR_ID));
    }

    @Test
    @DisplayName("文章为 null → 一律不可见")
    void nullArticleIsNotVisible() {
        assertFalse(ArticleVisibilityPolicy.canView(null, AUTHOR_ID));
        assertFalse(ArticleVisibilityPolicy.isPubliclyVisible(null));
    }

    // =====================================================================
    // 计划 Stage 5 的验收标准：
    // 「匿名/他人/作者/管理员四种身份 × 草稿/待审/已发布/已归档/私有，逐组合断言」
    // =====================================================================

    /**
     * 四种身份
     */
    private enum Identity {
        /** 匿名访客 */
        ANONYMOUS(null, false),
        /** 与作者无关的普通登录用户 */
        OTHER(OTHER_USER_ID, false),
        /** 文章作者本人 */
        AUTHOR(AUTHOR_ID, false),
        /** 拥有「管理他人资源」能力的管理者 */
        ADMIN(OTHER_USER_ID, true);

        private final Long userId;
        private final boolean canManageOthers;

        Identity(Long userId, boolean canManageOthers) {
            this.userId = userId;
            this.canManageOthers = canManageOthers;
        }

        boolean canView(Posts article) {
            return ArticleVisibilityPolicy.canView(article, userId, canManageOthers);
        }
    }

    /**
     * 五种文章状态
     */
    private enum ArticleState {
        DRAFT(Posts.Status.DRAFT, Posts.Visibility.PUBLIC),
        PENDING(Posts.Status.PENDING, Posts.Visibility.PUBLIC),
        PUBLISHED(Posts.Status.PUBLISHED, Posts.Visibility.PUBLIC),
        ARCHIVED(Posts.Status.ARCHIVED, Posts.Visibility.PUBLIC),
        PRIVATE(Posts.Status.PUBLISHED, Posts.Visibility.PRIVATE);

        private final Integer status;
        private final String visibility;

        ArticleState(Integer status, String visibility) {
            this.status = status;
            this.visibility = visibility;
        }
    }

    @Test
    @DisplayName("四种身份 × 五种文章状态：未公开的文章只对作者与管理员可见")
    void visibilityMatrix() {
        for (ArticleState state : ArticleState.values()) {
            Posts article = article(state.status, state.visibility, AUTHOR_ID);
            boolean published = state == ArticleState.PUBLISHED;

            for (Identity identity : Identity.values()) {
                boolean expected = published
                        // 已发布且公开 → 谁都能读
                        || identity == Identity.AUTHOR
                        // 作者本人 → 自己的文章永远能读
                        || identity == Identity.ADMIN;
                // 「能管理他人」的管理者与作者同档
                assertThat(identity.canView(article))
                        .as("状态=%s, 身份=%s", state, identity)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("管理员档与作者档同档，但都读不到「不存在的文章」")
    void adminTier_doesNotWidenBeyondOwner() {
        Posts draft = article(Posts.Status.DRAFT, Posts.Visibility.PUBLIC, AUTHOR_ID);

        // 管理员读得到别人的草稿
        assertTrue(ArticleVisibilityPolicy.canView(draft, OTHER_USER_ID, true));
        // 但普通用户读不到
        assertFalse(ArticleVisibilityPolicy.canView(draft, OTHER_USER_ID, false));
        // 匿名读不到
        assertFalse(ArticleVisibilityPolicy.canView(draft, null, false));
        // null 文章谁都读不到
        assertFalse(ArticleVisibilityPolicy.canView(null, OTHER_USER_ID, true));
    }

    @Test
    @DisplayName("两参重载等价于「不是管理员」——旧调用方的语义不变")
    void twoArgOverload_isEquivalentToNonAdmin() {
        for (ArticleState state : ArticleState.values()) {
            Posts article = article(state.status, state.visibility, AUTHOR_ID);
            assertThat(ArticleVisibilityPolicy.canView(article, OTHER_USER_ID))
                    .as("状态=%s", state)
                    .isEqualTo(ArticleVisibilityPolicy.canView(article, OTHER_USER_ID, false));
            assertThat(ArticleVisibilityPolicy.canView(article, AUTHOR_ID))
                    .as("状态=%s", state)
                    .isEqualTo(ArticleVisibilityPolicy.canView(article, AUTHOR_ID, false));
            assertThat(ArticleVisibilityPolicy.canView(article, null))
                    .as("状态=%s", state)
                    .isEqualTo(ArticleVisibilityPolicy.canView(article, null, false));
        }
    }

    private Posts article(Integer status, String visibility, Long userId) {
        Posts posts = new Posts();
        posts.setId(1L);
        posts.setStatus(status);
        posts.setVisibility(visibility);
        posts.setUserId(userId);
        posts.setIsDeleted(false);
        return posts;
    }
}
