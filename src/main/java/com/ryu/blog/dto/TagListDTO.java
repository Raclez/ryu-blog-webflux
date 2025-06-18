package com.ryu.blog.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "标签列表查询")
public class TagListDTO {


    @Schema(description = "当前页")
    private int currentPage;
    @Schema(description = "每页条数")
    private int  pageSize;

    @Schema(description = "分类的关键字")
    private String keyword;
}
