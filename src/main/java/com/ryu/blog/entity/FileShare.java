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
 * 文件分享表，用于存储文件的分享信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_file_shares")
public class FileShare implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分享记录的唯一标识
     */
    @Id
    private Long id;

    /**
     * 分享的文件ID
     */
    @Column("file_id")
    private Long fileId;

    /**
     * 分享者用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 分享链接的唯一标识符
     */
    @Column("share_key")
    private String shareKey;

    /**
     * 访问分享的密码（可为空）
     */
    private String password;

    /**
     * 分享链接的过期时间
     */
    @Column("expire_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /**
     * 允许的最大下载次数（0表示不限制）
     */
    @Column("max_downloads")
    private Integer maxDownloads;

    /**
     * 已下载次数
     */
    @Column("download_count")
    private Integer downloadCount;

    /**
     * 是否允许匿名访问
     */
    @Column("allow_anonymous")
    private Boolean allowAnonymous;

    /**
     * 分享的状态（1-有效；0-已取消；2-已过期）
     */
    private Integer status;

    /**
     * 分享的创建时间
     */
    @Column("create_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 最后访问时间
     */
    @Column("last_access_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAccessTime;

    /**
     * 分享说明
     */
    private String description;
} 