package com.ryu.blog.dto;

import lombok.Data;

/**
 * 存储策略查询DTO
 *
 * @author ryu 475118582@qq.com
 */
@Data
public class StorageConfigQueryDTO {
    /**
     * 当前页码
     */
    private Integer currentPage;

    /**
     * 每页大小
     */
    private Integer pageSize;

    /**
     * 策略名称
     */
    private String strategyName;

} 