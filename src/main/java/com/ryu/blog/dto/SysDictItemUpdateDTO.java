package com.ryu.blog.dto;


import lombok.Data;

@Data
public class SysDictItemUpdateDTO {
    private Long id;

    private Long dictTypeId;

    private String dictItemKey;

    private String dictItemValue;

    private Integer sort;

    private Integer status;

    private String lang;

    private String remark;

}
