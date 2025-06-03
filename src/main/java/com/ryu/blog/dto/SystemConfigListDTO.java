package com.ryu.blog.dto;

import lombok.Data;

@Data
public class SystemConfigListDTO {

    private Long currentPage;

    private Long pageSize;

    private String configKey;

}
