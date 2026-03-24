package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.validator.constraints.URL;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章更新数据传输对象
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "文章更新数据传输对象")
public class PostUpdateDTO {
    
    @NotNull(message = "博客ID不能为空")
    @Positive(message = "博客ID必须为正数")
    @Schema(description = "文章ID", example = "1")
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(min = 1, max = 200, message = "标题长度必须在1-200个字符之间")
    @Schema(description = "文章标题", example = "Spring Boot 实战教程")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(min = 1, max = 1000000, message = "内容长度不能超过1000000个字符")
    @Schema(description = "文章内容（Markdown格式）")
    private String content;

    @Size(max = 500, message = "摘要长度不能超过500个字符")
    @Schema(description = "文章摘要")
    private String excerpt;

    @Size(max = 200, message = "SEO标题长度不能超过200个字符")
    @Schema(description = "SEO标题")
    private String seoTitle;

    @Size(max = 500, message = "SEO描述长度不能超过500个字符")
    @Schema(description = "SEO描述")
    private String seoDescription;

    @Size(max = 200, message = "URL别名长度不能超过200个字符")
    @Pattern(regexp = "^[a-z0-9-]*$", message = "URL别名只能包含小写字母、数字和连字符")
    @Schema(description = "URL别名", example = "spring-boot-tutorial")
    private String slug;

    @Positive(message = "封面图片ID必须为正数")
    @Schema(description = "封面图片ID")
    private Long coverImageId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "定时发布时间", example = "2024-12-31 23:59:59")
    private LocalDateTime scheduleTime;

    @Min(value = 0, message = "置顶标识只能为0或1")
    @Max(value = 1, message = "置顶标识只能为0或1")
    @Schema(description = "是否置顶：0-否，1-是", example = "0")
    private Integer isSticky;

    @Schema(description = "是否原创", example = "true")
    private Boolean isOriginal;

    @Min(value = 0, message = "排序号不能为负数")
    @Max(value = 9999, message = "排序号不能超过9999")
    @Schema(description = "排序号", example = "0")
    private Integer sort;

    @Schema(description = "是否允许评论", example = "true")
    private Boolean allowComment;

    @Min(value = 0, message = "立即发布标识只能为0或1")
    @Max(value = 1, message = "立即发布标识只能为0或1")
    @Schema(description = "是否立即发布：0-否，1-是", example = "0")
    private Integer isPublishImmediately;
    
    @URL(message = "来源URL格式不正确")
    @Size(max = 500, message = "来源URL长度不能超过500个字符")
    @Schema(description = "文章来源URL")
    private String sourceUrl;
    
    @Size(max = 100, message = "许可证长度不能超过100个字符")
    @Schema(description = "文章许可证", example = "CC BY-NC-SA 4.0")
    private String license;

    @Pattern(regexp = "^(public|private|password)$", message = "访问权限只能为public、private或password")
    @Schema(description = "访问权限：public-公开，private-私密，password-密码访问", example = "public")
    private String visibility;

//    @Size(min = 4, max = 20, message = "访问密码长度必须在4-20个字符之间")
    @Schema(description = "访问密码（当visibility为password时必填）")
    private String password;

    @NotNull(message = "分类ID不能为空")
    @Positive(message = "分类ID必须为正数")
    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "标签ID列表")
    private List<String> tagsIds;

    @Min(value = 0, message = "状态值只能为0、1或2")
    @Max(value = 2, message = "状态值只能为0、1或2")
    @Schema(description = "文章状态：0-草稿，1-已发布，2-回收站", example = "1")
    private Integer status;
} 