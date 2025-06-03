package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统字典项视图对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统字典项视图对象")
public class SysDictItemVO {

    @Schema(description = "字典项ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    @Schema(description = "所属字典类型ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dictTypeId;
    
    @Schema(description = "所属字典类型编码")
    private String dictType;
    
    @Schema(description = "所属字典类型名称")
    private String typeName;

    @Schema(description = "字典项键")
    private String dictItemKey;
    
    @Schema(description = "字典项值")
    private String dictItemValue;
    
    @Schema(description = "排序字段")
    private Integer sort;
    
    @Schema(description = "状态：1 启用, 0 禁用")
    private Integer status;
    
    @Schema(description = "语言标识")
    private String lang;
    
    @Schema(description = "备注")
    private String remark;
    
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
} 