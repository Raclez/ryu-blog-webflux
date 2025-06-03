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
 * 标签信息视图对象
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "标签信息视图对象")
public class TagVO {
    
    @Schema(description = "标签ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    @Schema(description = "标签名称")
    private String name;
    
    @Schema(description = "标签别名")
    private String slug;
    
    @Schema(description = "标签描述")
    private String description;
    
    @Schema(description = "文章数量")
    private Long articleCount;
    
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
} 