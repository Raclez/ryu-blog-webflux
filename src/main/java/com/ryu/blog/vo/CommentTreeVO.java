package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论树形结构视图对象
 * 用于构建嵌套的评论回复结构
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "评论树形结构视图对象")
public class CommentTreeVO {

    @Schema(description = "评论ID")
    private Long id;

    @Schema(description = "文章ID")
    private Long postId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "用户头像URL")
    private String userAvatar;

    @Schema(description = "父评论ID")
    private Long parentCommentId;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "评论状态：1待审核，2已批准，3已拒绝")
    private Integer status;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "是否已编辑: 0否，1是")
    private Byte isEdited;

    @Schema(description = "回复数量")
    private Integer replyCount;

    @Schema(description = "编辑时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime editTime;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "子评论列表")
    private List<CommentTreeVO> children;
} 