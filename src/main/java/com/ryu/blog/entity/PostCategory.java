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
 * 实现文章与分类之间的多对多关系
 *
 * @author ryu 475118582@qq.com
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("t_post_categories")
public class PostCategory implements Serializable {
    private static final long serialVersionUID = -3329894548771268592L;
    
    /**
     * 关联ID
     */
    @Id
    private Long id;
    
    /**
     * 文章的唯一标识
     */
    @Column("post_id")
    private Long postId;

    /**
     * 分类的唯一标识
     */
    @Column("category_id")
    private Long categoryId;

    /**
     * 关联时间
     */
    @Column("create_time")
    private LocalDateTime createTime;
} 