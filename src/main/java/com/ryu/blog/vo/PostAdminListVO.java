package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台管理文章列表视图对象
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "后台管理文章列表视图对象")
public class PostAdminListVO {
    
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
    
    @Schema(description = "文章状态：0-草稿，1-已发布，2-回收站")
    private Integer status;
    
    @Schema(description = "作者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    
    @Schema(description = "作者名称")
    private String authorName;
    
    @Schema(description = "浏览量")
    private Integer views;

    @Schema(description = "是否原创")
    private Boolean isOriginal;
    
    @Schema(description = "是否允许评论")
    private Boolean allowComment;


    private String visibility;

    @Schema(description = "排序权重")
    private Integer sort;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;


    @Schema(description = "文章的发布时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;
} 