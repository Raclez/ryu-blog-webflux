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

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "配置描述")
    private String description;

    @Schema(description = "配置分组")
    private String configGroup;

    @Schema(description = "是否系统内置：0-否，1-是")
    private Integer isSystem;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
} 