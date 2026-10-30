package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

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
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Posts extends BaseEntity {

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
     * 文章别名（URL slug）
     *
     * <p>此前 PostCreateDTO / PostUpdateDTO 都声明了 slug，PostDetailVO 也对外暴露 slug，
     * 但实体里没有对应字段、{@code t_posts} 也没有这一列，
     * 于是客户端传的 slug 被 MapStruct 静默丢弃，详情接口返回的 slug 恒为空。
     * 这里补上真实字段，让 DTO/VO 的契约真正成立（可用于按别名查文章）。
     */
    private String slug;

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
     * 是否置顶
     *
     * <p>此前 {@code PostCreateDTO} / {@code PostUpdateDTO} 都声明了 {@code isSticky}，
     * 前端编辑器也有「置顶」开关，但实体里没有对应属性、{@code t_posts} 也没有这一列 ——
     * 客户端传的置顶标记被 MapStruct 静默丢弃，开关永远停在「关」。
     *
     * <p>类型必须用包装类 {@code Boolean} 而不是 {@code boolean}：
     * Lombok 对包装类生成的是 {@code getIsSticky()}（属性名 {@code isSticky}），
     * 对基本类型生成的是 {@code isSticky()}（属性名会变成 {@code sticky}）——
     * 后者会让 MapStruct / Jackson 两边的属性名对不上。
     */
    @Column("is_sticky")
    private Boolean isSticky;

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
     * 文章的发布时间
     */
    @Column("publish_time")
    private LocalDateTime publishTime;

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