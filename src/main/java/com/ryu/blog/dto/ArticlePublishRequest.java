package com.ryu.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章定时发布任务请求
 *
 * <p>存进 {@code t_async_tasks.request_json}，到点由
 * {@code ArticlePublishTaskHandler} 还原成这个类型执行。
 *
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticlePublishRequest {

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 计划发布时间
     *
     * <p>与文章的 {@code publish_time} 同值，任务到点时用它做诊断
     * （任务列表里能看到「这条任务原本排到几点」）。
     *
     * <p><b>是否到点以文章当前的 {@code publish_time} 为准</b>：
     * 文章被改期后，旧任务的这个值已经过期，而文章的新时间仍在未来 ——
     * 旧任务到点时自己跳过（不依赖任务取消是否成功）。
     */
    private LocalDateTime publishTime;
}
