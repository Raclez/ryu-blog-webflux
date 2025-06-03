package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 评论查询参数
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "评论查询参数")
public class CommentQueryDTO {
    @Schema(description = "当前页码")
    private Long currentPage;
    
    @Schema(description = "每页数量")
    private Long pageSize;
    
    @Schema(description = "文章ID")
    private Long postId;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "评论状态")
    private Integer status;
    
    @Schema(description = "关键词")
    private String keyword;
} 