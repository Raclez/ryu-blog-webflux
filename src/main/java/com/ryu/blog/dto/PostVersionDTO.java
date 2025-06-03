package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文章的历史版本DTO
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "文章的历史版本")
public class PostVersionDTO {
    
    @Schema(description = "文章id")
    private Long postId;
    
    @Schema(description = "版本信息")
    private Integer version;
} 