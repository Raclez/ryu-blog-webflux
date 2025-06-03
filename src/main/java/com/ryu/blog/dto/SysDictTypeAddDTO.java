package com.ryu.blog.dto;

import lombok.Data;

@Data
public class SysDictTypeAddDTO {

    private String dictType;
    private String typeName;
    private Integer status;
    private  String remark;

}
