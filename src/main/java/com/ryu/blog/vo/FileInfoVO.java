package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 文件详细信息视图对象
 *
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileInfoVO {
    
    /**
     * 文件ID
     */
    private Long id;
    
    /**
     * 文件名称
     */
    private String fileName;
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 格式化后的文件大小
     */
    private String formattedSize;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * MIME类型
     */
    private String mimeType;
    
    /**
     * 上传时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    
    /**
     * 上传用户ID
     */
    private Long creatorId;
    
    /**
     * 上传用户名称
     */
    private String creatorName;
    
    /**
     * 文件状态
     */
    private Integer status;
    
    /**
     * 存储类型
     */
    private String storageType;
    
    /**
     * 文件校验码
     */
    private String checksum;
    
    /**
     * 文件描述
     */
    private String description;
    
    /**
     * CDN链接
     */
    private String cdnUrl;
    
    /**
     * 文件预览链接
     */
    private String previewUrl;
    
    /**
     * 文件下载链接
     */
    private String downloadUrl;
    
    /**
     * 所属分组列表
     */
    private List<ResourceGroupVO> groups;
    
    /**
     * 文件元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 最新版本信息
     */
    private FileVersionVO latestVersion;
    
    /**
     * 文件图标
     */
    private String fileIcon;
    
    /**
     * 缩略图URL
     */
    private String thumbnailUrl;
    
    /**
     * 是否有缩略图
     */
    private Boolean hasThumbnail;
    
    /**
     * 访问类型
     */
    private Integer accessType;
    
    /**
     * 创建时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
} 