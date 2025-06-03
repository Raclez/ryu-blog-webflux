package com.ryu.blog.dto;

import lombok.Data;
import lombok.ToString;

/**
 * 文件更新DTO
 *
 * @author ryu
 */
@Data
@ToString
public class FilesDTO {
    
    /**
     * 文件名称
     */
    private String fileName;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 文件描述
     */
    private String description;
    
    /**
     * 文件状态
     */
    private Integer status;
    
    /**
     * 所属分组ID
     */
    private Long groupId;
} 