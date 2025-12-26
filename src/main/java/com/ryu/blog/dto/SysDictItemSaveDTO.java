package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "字典项保存DTO")
public class SysDictItemSaveDTO {

    @NotNull(message = "字典类型ID不能为空")
    @Schema(description = "字典类型ID", required = true, example = "1")
    private Long dictTypeId;

    @NotBlank(message = "字典项键不能为空")
    @Size(max = 100, message = "字典项键长度不能超过100")
    @Schema(description = "字典项键", required = true, example = "1")
    private String dictItemKey;

    @NotBlank(message = "字典项值不能为空")
    @Size(max = 200, message = "字典项值长度不能超过200")
    @Schema(description = "字典项值", required = true, example = "男")
    private String dictItemValue;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态：1-启用，0-禁用", example = "1")
    private Integer status;

    @Size(max = 500, message = "备注长度不能超过500")
    @Schema(description = "备注")
    private String remark;
}
