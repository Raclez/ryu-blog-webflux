package com.ryu.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章标签查询投影
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostTagProjection {
    private Long postId;
    private Long tagId;
    private String tagName;
}
