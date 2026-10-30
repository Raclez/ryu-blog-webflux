package com.ryu.blog.service.impl.article;

import com.ryu.blog.entity.Posts;
import com.ryu.blog.enums.PublishMode;
import com.ryu.blog.exception.BusinessException;

import java.time.LocalDateTime;

/**
 * 文章发布方式 → 状态/发布时间的映射规则
 *
 * <p><b>为什么单独拎出来</b>：这是一条纯规则（没有 IO、没有登录态），
 * 却决定「这篇文章什么时候对外可见」—— 写死在 Service 的私有方法里，
 * 就只能靠集成测试去验证。抽成纯函数后，规则可以被直接断言，
 * 改错了立刻红。
 *
 * <p>状态语义（与 {@link Posts.Status} 常量一一对应）：
 * <ul>
 *   <li>{@code DRAFT}（2）→ 草稿，{@code publish_time} 置空</li>
 *   <li>{@code IMMEDIATE}（1）→ 已发布，{@code publish_time} = 现在</li>
 *   <li>{@code SCHEDULED}（0）→ 待发布，{@code publish_time} = 计划时间</li>
 * </ul>
 *
 * <p>计划时间落在 {@code publish_time} 上而不是 {@code schedule_time}：
 * 后者从来没有被任何查询读过，前台可见性一律由 {@code status} 驱动，
 * 两者并存只会出现「两个时间列各说各话」。
 *
 * @author ryu
 */
public final class ArticlePublishPolicy {

    private ArticlePublishPolicy() {
    }

    /**
     * 按发布方式设置文章状态与发布时间
     *
     * @param article      文章实体（原地修改）
     * @param mode         发布方式；{@code null} 视为 {@link PublishMode#DRAFT}
     * @param scheduleTime 计划发布时间（仅 {@code SCHEDULED} 使用）
     * @throws BusinessException {@code SCHEDULED} 但缺少计划时间，或计划时间不在未来
     */
    public static void apply(Posts article, PublishMode mode, LocalDateTime scheduleTime) {
        PublishMode effectiveMode = mode != null ? mode : PublishMode.DRAFT;

        switch (effectiveMode) {
            case IMMEDIATE -> {
                // 已经发布过的文章**不重置发布时间**。
                //
                // 前台按 publish_time 排序，编辑器里改个错别字点「发布」就把首发日期改成现在，
                // 等于每次编辑都把文章顶到最新 —— 老文章会集体「返老还童」。
                // 这条规则与 updateArticleStatus 的既有行为一致（那边也是只在 publish_time 为空时才写）。
                //
                // 但「从草稿/待发布改成立即发布」必须写现在：
                // 那两种状态下 publish_time 要么是空、要么是还没到的计划时间，都不能再留着。
                boolean wasPublished = article.getStatus() != null
                        && article.getStatus() == Posts.Status.PUBLISHED;
                article.setStatus(Posts.Status.PUBLISHED);
                if (!wasPublished || article.getPublishTime() == null) {
                    article.setPublishTime(LocalDateTime.now());
                }
            }
            case SCHEDULED -> {
                if (scheduleTime == null) {
                    throw new BusinessException("定时发布时间不能为空");
                }
                if (!scheduleTime.isAfter(LocalDateTime.now())) {
                    throw new BusinessException("定时发布时间必须晚于当前时间");
                }
                article.setStatus(Posts.Status.PENDING);
                article.setPublishTime(scheduleTime);
            }
            default -> {
                article.setStatus(Posts.Status.DRAFT);
                article.setPublishTime(null);
            }
        }
    }
}
