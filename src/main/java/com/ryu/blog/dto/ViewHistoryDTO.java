package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 浏览历史数据传输对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "浏览历史数据传输对象")
public class ViewHistoryDTO {

    /**
     * 浏览文章的游客标识
     */
    @Schema(description = "浏览文章的游客标识")
    private String visitorId;

    /**
     * 被浏览文章的唯一标识
     */
    @Schema(description = "被浏览文章的唯一标识")
    private Long postId;

    @Schema(description = "浏览时长（秒）")
    private Integer viewDuration;

    @Schema(description = "来源页面")
    private String referrer;
} 