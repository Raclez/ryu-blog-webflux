package com.ryu.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章创建数据传输对象
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "文章创建数据传输对象")
public class PostCreateDTO {
    
    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    private String excerpt;

    private Long coverImageId;

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    private List<String> tagsIds;

    private String seoTitle; // SEO标题

    private String seoDescription; // SEO描述

    private String slug; // URL别名

    private Boolean isSticky; // 是否置顶

    private Boolean isOriginal; // 是否原创

    private Integer sort; // 排序号

    private Boolean allowComment; // 是否允许评论

    private String license;
    
    private String visibility; // 访问权限: public, private, password
    
    private String sourceUrl;
    
    private String password; // 访问密码

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduleTime; // 定时发布时间

    private Boolean isPublishImmediately;
} 