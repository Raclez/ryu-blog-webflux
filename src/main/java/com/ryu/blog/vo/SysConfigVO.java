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
 * 系统配置视图对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统配置视图对象")
public class SysConfigVO {

    @Schema(description = "配置ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "配置键，格式为'分组.子分组.配置名'")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：true 启用, false 禁用")
    private Boolean status;

    @Schema(description = "用户ID，0表示全局配置")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "扩展信息")
    private String extra;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
} 