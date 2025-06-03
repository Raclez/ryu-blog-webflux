package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存储用户对文章的评论信息
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "存储用户对文章的评论信息")
public class CommentVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "评论的唯一标识")
    private Long id;

    @Schema(description = "被评论文章的唯一标识")
    private Long postId;

    @Schema(description = "发表评论用户的唯一标识")
    private Long userId;

    @Schema(description = "父评论的唯一标识，用于支持嵌套评论")
    private Long parentCommentId;

    @Schema(description = "评论的内容")
    private String content;

    @Schema(description = "评论的审核状态：已批准、待审核、已拒绝")
    private Integer status;

    @Schema(description = "评论创建的时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "评论的最后更新时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
} 