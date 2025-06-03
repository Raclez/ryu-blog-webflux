package com.ryu.blog.dto;

import lombok.Data;

@Data
public class SysConfigDTO {

    private String configKey;

    private String configValue;

    private Long dictId;

    private Integer status;

    private String remark;

    private String extra;


}