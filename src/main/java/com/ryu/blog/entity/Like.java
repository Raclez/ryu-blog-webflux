package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 存储用户对文章的点赞信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_likes")
@EqualsAndHashCode(callSuper = true, of = {"id"})
public class Like extends BaseEntity {

    /**
     * 用户的唯一标识
     */
    @Column("user_id")
    private Long userId;

    /**
     * 对象类型，如 post, comment
     */
    @Column("object_type")
    private String type;

    /**
     * 被点赞对象ID
     */
    @Column("object_id")
    private Long objectId;
} 