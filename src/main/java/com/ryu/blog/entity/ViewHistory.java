package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存储用户对文章的浏览历史
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_view_history")
public class ViewHistory implements Serializable {
    private static final long serialVersionUID = -692747696481717282L;

    /**
     * 浏览历史记录的唯一标识
     */
    @Id
    private Long id;

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
    
    /**
     * 创建时间
     */
    @Column("create_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Integer isDeleted;
} 