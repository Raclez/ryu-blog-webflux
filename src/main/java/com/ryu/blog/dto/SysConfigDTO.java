package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 系统配置数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统配置数据传输对象")
public class SysConfigDTO {

    @NotBlank(message = "配置键不能为空")
    @Schema(description = "配置键，格式为'分组.子分组.配置名'", required = true)
    private String configKey;

    @NotBlank(message = "配置值不能为空")
    @Schema(description = "配置值", required = true)
    private String configValue;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：true 启用, false 禁用")
    private Boolean status;

    @Schema(description = "用户ID，0表示全局配置")
    private Long userId;

    @Size(max = 1000, message = "扩展信息长度不能超过1000")
    @Schema(description = "扩展信息")
    private String extra;
}