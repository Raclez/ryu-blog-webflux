package com.ryu.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章分类查询投影
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCategoryProjection {
    private Long postId;
    private Long categoryId;
    private String categoryName;
}
