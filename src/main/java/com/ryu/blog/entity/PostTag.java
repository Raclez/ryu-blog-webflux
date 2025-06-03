package com.ryu.blog.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实现文章与标签之间的多对多关系
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_post_tags")
public class PostTag implements Serializable {
    private static final long serialVersionUID = -8994288556419907282L;

    @Id
    private Long id;

    /**
     * 文章的唯一标识
     */
    @Column("post_id")
    private Long postId;

    /**
     * 标签的唯一标识
     */
    @Column("tag_id")
    private Long tagId;

    /**
     * 关联时间
     */
    @Column("create_time")
    private LocalDateTime createTime;
} 