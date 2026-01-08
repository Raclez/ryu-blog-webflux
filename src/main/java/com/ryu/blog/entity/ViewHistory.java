package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 存储用户对文章的浏览历史
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_view_history")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class ViewHistory extends BaseEntity {

    /**
     * 浏览文章的游客标识
     */
    @Column("visitor_id")
    private String visitorId;

    /**
     * 被浏览文章的唯一标识
     */
    @Column("post_id")
    private Long postId;

    /**
     * 浏览时间
     */
    @Column("view_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime viewTime;

    /**
     * 游客ip地址
     */
    @Column("ip_address")
    private String ipAddress;

    /**
     * 游客设备
     */
    private String agent;

    /**
     * 地理位置
     */
    private String location;

    /**
     * 浏览时长（秒）
     */
    @Column("view_duration")
    private Integer viewDuration;

    /**
     * 来源页面
     */
    private String referer;
} 