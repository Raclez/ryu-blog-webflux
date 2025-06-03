package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章统计数据视图对象
 * 用于展示博客系统的整体统计数据
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章统计数据")
public class PostStatisticsVO {

    @Schema(description = "总文章数量")
    private Integer totalPosts;
    
    @Schema(description = "已发布文章数量")
    private Integer publishedPosts;
    
    @Schema(description = "草稿文章数量")
    private Integer draftPosts;
    
    @Schema(description = "总阅读量")
    private Long totalViews;
    
    @Schema(description = "总点赞数")
    private Long totalLikes;
    
    @Schema(description = "总评论数")
    private Long totalComments;
    
    @Schema(description = "平均阅读量")
    private Double averageViews;
    
    @Schema(description = "平均评论数")
    private Double averageComments;
    
    @Schema(description = "总标签数")
    private Integer totalTags;
    
    @Schema(description = "总分类数")
    private Integer totalCategories;
    
    @Schema(description = "最近发布时间")
    private LocalDateTime lastPublishTime;
    
    @Schema(description = "热门分类ID")
    private Long hotCategoryId;
    
    @Schema(description = "热门分类名称")
    private String hotCategoryName;
    
    @Schema(description = "热门标签ID")
    private Long hotTagId;
    
    @Schema(description = "热门标签名称")
    private String hotTagName;
} 