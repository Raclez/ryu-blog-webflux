package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "字典类型添加DTO")
public class SysDictTypeAddDTO {

    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 100, message = "字典类型编码长度不能超过100")
    @Schema(description = "字典类型编码", required = true, example = "gender_complete")
    private String dictType;
    
    @NotBlank(message = "字典类型名称不能为空")
    @Size(max = 100, message = "字典类型名称长度不能超过100")
    @Schema(description = "字典类型名称", required = true, example = "性别类型")
    private String typeName;
    
    @Schema(description = "状态：1-启用，0-禁用", example = "1")
    private Integer status;
    
    @Size(max = 500, message = "备注长度不能超过500")
    @Schema(description = "备注", example = "用户性别选项")
    private String remark;
}
