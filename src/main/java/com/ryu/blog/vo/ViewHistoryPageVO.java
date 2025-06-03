package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 浏览历史分页查询结果
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "浏览历史分页查询结果")
public class ViewHistoryPageVO {
    
    @Schema(description = "当前页码")
    private Long current;

    @Schema(description = "每页大小")
    private Long size;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "总页数")
    private Long pages;

    @Schema(description = "浏览历史记录列表")
    private List<ViewHistoryVO> records;

    @Schema(description = "是否有上一页")
    private Boolean hasPrevious;

    @Schema(description = "是否有下一页")
    private Boolean hasNext;
}