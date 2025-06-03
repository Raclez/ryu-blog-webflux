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
 * 存储文章的历史版本
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_post_versions")
public class PostVersion implements Serializable {
    private static final long serialVersionUID = 4352136864701161441L;

    /**
     * 文章版本的唯一标识
     */
    @Id
    private Long id;

    /**
     * 文章的唯一标识
     */
    @Column("post_id")
    private Long postId;

    /**
     * 文章的版本号
     */
    private Integer version;

    /**
     * 文章在该版本的内容
     */
    private String content;

    /**
     * 编辑者ID（引用 auth_service.t_users）
     */
    private Long editor;

    /**
     * 修改日志或备注
     */
    @Column("change_log")
    private String changeLog;

    /**
     * 字数统计
     */
    @Column("word_count")
    private Integer wordCount;

    /**
     * 版本标签（JSON数组）
     */
    private String tags;

    /**
     * 是否为最新版本
     */
    @Column("is_latest")
    private Boolean isLatest;

    /**
     * 版本描述
     */
    private String description;

    /**
     * 编辑耗时（分钟）
     */
    private Integer duration;

    /**
     * 修改次数
     */
    @Column("modify_count")
    private Integer modifyCount;

    /**
     * 版本创建的时间
     */
    @Column("create_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 版本更新时间
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