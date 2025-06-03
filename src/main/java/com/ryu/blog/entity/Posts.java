package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存储博客文章的详细信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Table("t_posts")
@AllArgsConstructor
@NoArgsConstructor
public class Posts implements Serializable {
    private static final long serialVersionUID = 4131038251292305030L;

    /**
     * 文章的唯一标识
     */
    @Id
    private Long id;

    /**
     * 文章作者的用户标识
     */
    @Column("user_id")
    private Long userId;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章的内容
     */
    private String content;

    /**
     * 文章摘要
     */
    private String excerpt;

    /**
     * 状态：
     */
    private Integer status;

    /**
     * 文章的浏览次数
     */
    private Integer views;

    /**
     * SEO信息（关键词、描述等）json
     */
    @Column("seo_meta")
    private String seoMeta;

    /**
     * 封面图片ID
     */
    @Column("cover_image_id")
    private Long coverImageId;

    /**
     * 是否原创
     */
    @Column("is_original")
    private Boolean isOriginal;

    /**
     * 来源URL
     */
    @Column("source_url")
    private String sourceUrl;

    /**
     * 排序权重
     */
    private Integer sort;

    /**
     * 是否允许评论
     */
    @Column("allow_comment")
    private Boolean allowComment;

    /**
     * 访问权限: public, private, password
     */
    private String visibility;

    /**
     * 访问密码
     */
    private String password;

    /**
     * 许可证
     */
    private String license;

    /**
     * 定时发布时间
     */
    @Column("schedule_time")
    private LocalDateTime scheduleTime;

    /**
     * 文章的创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 文章的最后更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    /**
     * 文章的发布时间
     */
    @Column("publish_time")
    private LocalDateTime publishTime;
    
    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Column("is_deleted")
    private Integer isDeleted;

    /**
     * 文章状态枚举
     */
    public static class Status {
        public static final int DRAFT = 2;      // 草稿
        public static final int PENDING = 0;    // 待发布
        public static final int PUBLISHED = 1;  // 已发布
        public static final int ARCHIVED = 3;   // 已归档
    }

    /**
     * 访问权限枚举
     */
    public static class Visibility {
        public static final String PUBLIC = "public";
        public static final String PRIVATE = "private";
        public static final String PASSWORD = "password";
    }
} 