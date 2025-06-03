package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 浏览历史详细信息VO
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "浏览历史详细信息")
public class ViewHistoryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "浏览历史记录的唯一标识")
    private Long id;

    @Schema(description = "浏览文章的游客标识")
    private String visitorId;

    @Schema(description = "被浏览文章的唯一标识")
    private Long postId;

    @Schema(description = "文章标题")
    private String postTitle;

    @Schema(description = "文章封面图片ID")
    private Long coverImageId;

    @Schema(description = "浏览时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime viewTime;

    @Schema(description = "游客ip地址")
    private String ipAddress;

    @Schema(description = "游客设备")
    private String agent;

    @Schema(description = "设备类型（移动设备、桌面设备等）")
    private String deviceType;

    @Schema(description = "地理位置")
    private String location;

    @Schema(description = "浏览时长（秒）")
    private Integer viewDuration;

    @Schema(description = "来源页面")
    private String referer;
} 