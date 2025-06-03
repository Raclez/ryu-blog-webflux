package com.ryu.blog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadVO {
    
    /**
     * 文件ID
     */
    private Long fileId;
    
    /**
     * 文件名称
     */
    private String fileName;
    
    /**
     * 文件URL
     */
    private String fileUrl;
    
    /**
     * 文件大小
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
     * 上传时间
     */
    private String uploadTime;
} 