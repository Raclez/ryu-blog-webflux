package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 前台文章列表视图对象，适用于游标分页
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "前台文章列表视图对象")
public class PostFrontListVO {
    
    @Schema(description = "文章ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    @Schema(description = "文章标题")
    private String title;
    
    @Schema(description = "文章摘要")
    private String excerpt;
    
    @Schema(description = "分类ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;
    
    @Schema(description = "分类名称")
    private String categoryName;
    
    @Schema(description = "作者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    
    @Schema(description = "作者名称")
    private String authorName;
    
    @Schema(description = "作者头像")
    private String authorAvatar;
    
    @Schema(description = "标签列表")
    private List<String> tags;
    
    @Schema(description = "文章封面")
    private String coverImageUrl;
    
    @Schema(description = "浏览量")
    private Integer views;
    
    @Schema(description = "点赞数")
    private Integer likeCount;
    
    @Schema(description = "评论数")
    private Integer commentCount;
    
    @Schema(description = "是否原创")
    private Boolean isOriginal;
    
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
} 