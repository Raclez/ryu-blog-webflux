package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存储文章的历史版本
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "存储文章的历史版本")
public class PostVersionVO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Schema(description = "版本ID")
    private Long id;

    @Schema(description = "文章ID")
    private Long postId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "操作人")
    private String editor;

    @Schema(description = "变更说明")
    private String changeLog;

    @Schema(description = "版本完整内容字数")
    private Integer wordCount;

    @Schema(description = "标签")
    private String tags;

    @Schema(description = "是否为最新版本")
    private boolean latest;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "编辑时长（秒）")
    private Integer duration;

    @Schema(description = "修改次数")
    private Integer modifyCount;
} 