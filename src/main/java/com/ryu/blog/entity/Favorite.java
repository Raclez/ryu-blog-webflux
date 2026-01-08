package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 存储用户收藏的文章
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_favorites")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class Favorite extends BaseEntity {

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