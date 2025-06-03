package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存储用户收藏的文章
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_favorites")
public class Favorite implements Serializable {
    private static final long serialVersionUID = 5840604561304187679L;

    /**
     * 收藏的唯一标识
     */
    @Id
    private Long id;

    /**
     * 文章的唯一标识
     */
    @Column("post_id")
    private Long postId;

    /**
     * 用户的唯一标识
     */
    @Column("user_id")
    private Long userId;

    /**
     * 收藏的时间
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

    /**
     * 文章信息（非数据库字段）
     */
    @Transient
    private Posts article;

    /**
     * 用户信息（非数据库字段）
     */
    @Transient
    private User user;
} 