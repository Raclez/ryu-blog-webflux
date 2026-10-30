package com.ryu.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章定时发布任务结果
 *
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticlePublishResult {

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 是否真的发布了
     */
    private boolean published;

    /**
     * 是否被跳过
     *
     * <p>「跳过」<b>不是失败</b>：文章已被手动发布、已被取消定时、或已被删除时，
     * 任务到点无事可做，直接跳过并记一笔日志。
     */
    private boolean skipped;

    /**
     * 跳过的原因 / 发布结果说明
     */
    private String reason;

    /**
     * 构造一个「已跳过」的结果
     */
    public static ArticlePublishResult skipped(Long postId, String title, String reason) {
        return ArticlePublishResult.builder()
                .postId(postId)
                .title(title)
                .published(false)
                .skipped(true)
                .reason(reason)
                .build();
    }

    /**
     * 构造一个「已发布」的结果
     */
    public static ArticlePublishResult published(Long postId, String title) {
        return ArticlePublishResult.builder()
                .postId(postId)
                .title(title)
                .published(true)
                .skipped(false)
                .reason("已发布")
                .build();
    }
}
