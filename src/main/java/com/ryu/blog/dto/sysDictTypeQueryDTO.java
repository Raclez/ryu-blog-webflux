package com.ryu.blog.dto;

import lombok.Data;

/**
 * 字典类型查询DTO
 * <p>
 * 用于封装字典类型查询条件
 * </p>
 *
 * @author Ryu
 * @since 2025-01-24
 */
@Data
public class sysDictTypeQueryDTO {

    /**
     * 当前页码
     */
    private Long currentPage = 1L;

    /**
     * 每页记录数
     */
    private Long pageSize = 10L;

    /**
     * 类型名称
     */
    private String typeName;
    
    /**
     * 类型编码
     */
    private String typeCode;
}
