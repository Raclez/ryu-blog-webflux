package com.ryu.blog.dto;

import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.List;

/**
 * 文件搜索参数DTO
 *
 * @author ryu
 */
@Data
@ToString
public class FileSearchDTO {
    
    /**
     * 文件名关键字
     */
    private String keyword;
    
    /**
     * 文件类型列表
     */
    private List<String> fileTypes;
    
    /**
     * 创建者ID
     */
    private Long creatorId;
    
    /**
     * 上传开始时间
     */
    private Date uploadStartTime;
    
    /**
     * 上传结束时间
     */
    private Date uploadEndTime;
    
    /**
     * 文件大小下限(字节)
     */
    private Long minSize;
    
    /**
     * 文件大小上限(字节)
     */
    private Long maxSize;
    
    /**
     * 分组ID
     */
    private Long groupId;
    
    /**
     * 排序字段
     */
    private String orderBy = "uploadTime";
    
    /**
     * 排序方向
     */
    private String orderDirection = "desc";
    
    /**
     * 当前页码
     */
    private Integer current = 1;
    
    /**
     * 每页大小
     */
    private Integer size = 20;
} 