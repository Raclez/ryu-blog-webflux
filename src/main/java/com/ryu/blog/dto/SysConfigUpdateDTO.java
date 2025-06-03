package com.ryu.blog.dto;

import lombok.Data;

@Data
public class SysConfigUpdateDTO {
    private Long id;
    private String configKey;
    private String configValue;
    private Long dictId;
    private Integer status;
    private String extra;
    private String remark;

}
