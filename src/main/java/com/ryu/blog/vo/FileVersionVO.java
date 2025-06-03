package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 文件版本信息视图对象
 *
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileVersionVO {
    
    /**
     * 版本ID
     */
    private Long id;
    
    /**
     * 文件ID
     */
    private Long fileId;
    
    /**
     * 版本号
     */
    private Integer versionNumber;
    
    /**
     * 版本标签
     */
    private String versionTag;
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 文件大小
     */
    private Long fileSize;
    
    /**
     * 格式化后的文件大小
     */
    private String formattedSize;
    
    /**
     * 文件校验码
     */
    private String checksum;
    
    /**
     * 创建者ID
     */
    private Long creatorId;
    
    /**
     * 创建者名称
     */
    private String creatorName;
    
    /**
     * 文件版本描述
     */
    private String description;
    
    /**
     * 是否是当前版本
     */
    private Boolean isCurrent;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    
    /**
     * 文件预览URL
     */
    private String previewUrl;
    
    /**
     * 文件下载URL
     */
    private String downloadUrl;
} 