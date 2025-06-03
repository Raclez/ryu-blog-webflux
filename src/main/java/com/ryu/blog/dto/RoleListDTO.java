package com.ryu.blog.dto;

import lombok.Data;

@Data
public class RoleListDTO {
    private Long currentPage;
    private Long pageSize;
    private String name;

}
