package com.ryu.blog.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 文章详情视图对象
 *
 * <p><b>刻意不包含</b> {@code password}（文章访问密码）：那是 {@code t_posts} 里的
 * 明文密钥，只用于服务端校验访问请求，绝不能随详情响应返回给客户端。
 * 需要改密码的作者走的是更新接口（{@code PostUpdateDTO}），不需要先读到明文。
 *
 * @author ryu 475118582@qq.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章详情视图对象")
public class PostDetailVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String content;
    private String excerpt;
    private Boolean isOriginal;
    private String slug;
    private String seoTitle;
    private String seoDescription;
    private Integer sort;
    private Boolean allowComment;

    @Schema(description = "是否置顶")
    private Boolean isSticky;
    private Integer status;
    private String sourceUrl;
    private String visibility;
    private String license;
    private Long categoryId;
    private List<Long> tagsIds;
    private String coverImageUrl;
    private Long coverImageId;

    @Schema(description = "发布时间；对「待发布」的文章就是计划发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "预计阅读时间（分钟）")
    private Integer readingTime;
} 