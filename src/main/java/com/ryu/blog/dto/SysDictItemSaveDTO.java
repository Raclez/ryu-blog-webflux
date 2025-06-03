package com.ryu.blog.dto;

import lombok.Data;

@Data
public class SysDictItemSaveDTO {

    private Long dictTypeId;

    private String dictItemKey;

    private String dictItemValue;

    private Integer sort;

    private Integer status;

    private String lang;

    private String remark;

}
