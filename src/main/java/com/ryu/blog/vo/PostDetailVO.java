package com.ryu.blog.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文章详情视图对象
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
    private Integer status;
    private String sourceUrl;
    private String visibility;
    private String password;
    private String license;
    private Long categoryId;
    private List<Long> tagsIds;
    private String coverImageUrl;
    private Long coverImageId;
} 