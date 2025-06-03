package com.ryu.blog.dto;

import lombok.Data;

@Data
public class SysDictTypeUpdateDTO {

    private Long id;
    private String dictType;
    private String typeName;
    private Integer status;
    private  String remark;

}
