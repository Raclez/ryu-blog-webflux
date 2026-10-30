package com.ryu.blog.service.impl.article;

import com.ryu.blog.entity.Posts;

/**
 * 文章可见性规则
 *
 * <p>规则只有一条，但原先散在两个 Service 里各写一遍
 * （文章详情 {@code ArticleQueryService} 与版本历史 {@code ArticleVersionServiceImpl}），
 * 很容易改一处漏一处。这里收敛成纯函数，便于直接测。
 *
 * <p><b>规则</b>：只有「已发布」且「公开」的文章才能被匿名读到；
 * 其余情况（草稿 / 待发布 / 已归档 / private / password）只对<b>作者本人</b>与
 * <b>「能管理他人」的管理者</b>可见。
 *
 * <p>「管理员可见」这一档与 {@code OwnershipPolicy.canActOn} 用的<b>是同一个能力位</b>
 * （{@code system:user:manage}）：写路径（改别人文章、回滚别人版本）早就允许管理员，
 * 读路径若不许，管理员就能改却读不到 —— 后台编辑器打不开别人的草稿。
 * 两边必须同档。
 *
 * <p><b>缓存注意</b>：可见性依赖两个输入 —— 「是谁」和「能不能管别人」。
 * 凡是缓存了本规则判定结果的缓存，key 里必须<b>同时</b>带上这两项，
 * 否则管理员读到的草稿详情会被缓存下来发给同 ID 的普通用户。
 *
 * @author ryu
 * @since 1.0.0
 */
public final class ArticleVisibilityPolicy {

    private ArticleVisibilityPolicy() {
    }

    /**
     * 是否是「公开可见」的文章（已发布 + 公开）
     *
     * @param article 文章
     * @return 公开可见返回 true
     */
    public static boolean isPubliclyVisible(Posts article) {
        if (article == null) {
            return false;
        }
        boolean published = article.getStatus() != null
                && article.getStatus() == Posts.Status.PUBLISHED;
        boolean publicVisibility = article.getVisibility() == null
                || Posts.Visibility.PUBLIC.equals(article.getVisibility());
        return published && publicVisibility;
    }

    /**
     * 当前用户能否读到这篇文章（不含管理员档）
     *
     * @param article       文章
     * @param currentUserId 当前登录用户ID（未登录为 null）
     * @return 能读到返回 true
     */
    public static boolean canView(Posts article, Long currentUserId) {
        return canView(article, currentUserId, false);
    }

    /**
     * 当前用户能否读到这篇文章
     *
     * @param article         文章
     * @param currentUserId   当前登录用户ID（未登录为 null）
     * @param canManageOthers 当前操作者是否拥有「管理他人资源」的能力
     *                        （{@code system:user:manage}）
     * @return 能读到返回 true
     */
    public static boolean canView(Posts article, Long currentUserId, boolean canManageOthers) {
        if (article == null) {
            return false;
        }
        if (isPubliclyVisible(article)) {
            return true;
        }
        // 未公开 → 作者本人
        if (currentUserId != null
                && article.getUserId() != null
                && currentUserId.equals(article.getUserId())) {
            return true;
        }
        // 未公开 → 「能管理他人」的管理者（与写路径 OwnershipPolicy.canActOn 同档）
        return canManageOthers;
    }
}
