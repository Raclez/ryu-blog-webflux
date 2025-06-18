package com.ryu.blog.dto;

import lombok.Data;

@Data
public class PermissionsQueryDTO {
    private Long currentPage;

    private Long  pageSize;

    private String name;

    private String identity;
    
    /**
     * 模块前缀，用于按模块查询权限
     */
    private String modulePrefix;
}
