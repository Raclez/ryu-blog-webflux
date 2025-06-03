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
 * 存储用户对文章的评论信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_comments")
public class Comment implements Serializable {
    private static final long serialVersionUID = -2651673892943466771L;

    /**
     * 评论的唯一标识
     */
    @Id
    private Long id;

    /**
     * 被评论文章的唯一标识
     */
    @Column("post_id")
    private Long postId;

    /**
     * 发表评论用户的唯一标识
     */
    @Column("user_id")
    private Long userId;

    /**
     * 父评论的唯一标识，用于支持嵌套评论
     */
    @Column("parent_comment_id")
    private Long parentCommentId;

    /**
     * 评论的内容
     */
    private String content;

    /**
     * 评论的审核状态：已批准、待审核、已拒绝
     */
    private Integer status;
    
    /**
     * 点赞数
     */
    @Column("like_count")
    private Integer likeCount;
    
    /**
     * 是否编辑过，0-否，1-是
     */
    @Column("is_edited")
    private Byte isEdited;
    
    /**
     * 编辑时间
     */
    @Column("edit_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime editTime;

    /**
     * 评论创建的时间
     */
    @Column("create_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 评论的最后更新时间
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