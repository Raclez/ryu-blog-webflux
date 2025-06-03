package com.ryu.blog.dto;

import lombok.Data;

@Data
public class PermissionsQueryDTO {
    private Long currentPage;

    private Long  pageSize;

    private String name;

    private String identity;
    private String module;
}
