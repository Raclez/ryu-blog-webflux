package com.ryu.blog.enums;

import lombok.Getter;

/**
 * 文章发布方式
 *
 * <p>对应 {@code t_posts.status} 与 {@code publish_time} 的三种组合：
 *
 * <table border="1">
 *   <caption>状态映射</caption>
 *   <tr><th>发布方式</th><th>status</th><th>publish_time</th><th>前台可见</th></tr>
 *   <tr><td>DRAFT 存草稿</td><td>2</td><td>NULL</td><td>否</td></tr>
 *   <tr><td>IMMEDIATE 立即发布</td><td>1</td><td>now</td><td>是</td></tr>
 *   <tr><td>SCHEDULED 定时发布</td><td>0（待发布）</td><td>计划时间</td><td>否</td></tr>
 * </table>
 *
 * <p>「待发布」（0）在引入定时发布之前从未被写入过，重新定义它不破坏任何既有逻辑。
 *
 * @author ryu
 */
@Getter
public enum PublishMode {

    /**
     * 存为草稿
     */
    DRAFT("存为草稿"),

    /**
     * 立即发布
     */
    IMMEDIATE("立即发布"),

    /**
     * 定时发布：到点由延迟任务自动发布
     */
    SCHEDULED("定时发布");

    private final String description;

    PublishMode(String description) {
        this.description = description;
    }
}
