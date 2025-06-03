package com.ryu.blog.dto;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class UploadOptionsDTO {
    private String storage;
    private Long group;
    private String access;
    private String versionPolicy;
    private String versionTag;
    private String description;
    
    /**
     * 文件大小(字节)，分片上传时使用
     */
    private Long fileSize;
    
    /**
     * 文件名，分片上传时使用
     */
    private String fileName;
    
    /**
     * 重复文件处理策略
     * reject - 拒绝上传（默认）
     * replace - 使用已存在的文件
     * rename - 重命名新文件
     */
    private String duplicateStrategy = "reject";

    public String getDuplicateStrategy() {
        return duplicateStrategy;
    }
} 