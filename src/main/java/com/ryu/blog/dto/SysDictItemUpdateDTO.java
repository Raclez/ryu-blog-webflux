package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "字典项更新DTO")
public class SysDictItemUpdateDTO {
    
    @NotNull(message = "字典项ID不能为空")
    @Schema(description = "字典项ID", required = true)
    private Long id;

    @Schema(description = "字典类型ID")
    private Long dictTypeId;

    @Size(max = 100, message = "字典项键长度不能超过100")
    @Schema(description = "字典项键")
    private String dictItemKey;

    @Size(max = 200, message = "字典项值长度不能超过200")
    @Schema(description = "字典项值")
    private String dictItemValue;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：1-启用，0-禁用")
    private Integer status;

    @Size(max = 500, message = "备注长度不能超过500")
    @Schema(description = "备注")
    private String remark;
}
