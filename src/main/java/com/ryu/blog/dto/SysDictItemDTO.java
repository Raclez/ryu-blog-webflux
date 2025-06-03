package com.ryu.blog.dto;

import lombok.Data;

/**
 * 字典项查询DTO
 * <p>
 * 用于封装字典项查询条件
 * </p>
 *
 * @author Ryu
 * @since 2025-01-24
 */
@Data
public class SysDictItemDTO {

    /**
     * 当前页码
     */
    private Long currentPage = 1L;
    
    /**
     * 每页记录数
     */
    private Long pageSize = 10L;
    
    /**
     * 字典类型
     */
    private String dictType;
    
    /**
     * 字典项标签
     */
    private String itemLabel;
}
