package com.ryu.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 存储策略创建DTO
 *
 * @author ryu 475118582@qq.com
 */
@Data
public class StorageConfigCreateDTO {

    /**
     * 策略名称
     */
    @NotBlank(message = "策略名称不能为空")
    private String strategyName;

    /**
     * 策略标识，如 local, oss, minio
     */
    @NotBlank(message = "策略标识不能为空")
    private String strategyKey;

    /**
     * 存储服务的配置信息
     */
    @NotNull(message = "存储配置不能为空")
    private Map<String, String> config;

    /**
     * 是否启用此策略
     *
     * <p>用 {@link Boolean} 而不是 {@code Integer}：前端开关回传的是 {@code true/false}，
     * Jackson 不会把布尔强转成整型 —— 声明成 {@code Integer} 时请求在进 Controller 之前
     * 就 400（{@code Cannot deserialize value of type java.lang.Integer from Boolean}）。
     * 反过来 {@code 1/0} 也能正常读成 {@code true/false}，兼容老调用方。
     */
    private Boolean isEnable;

    /**
     * 访问URL
     */
    private String accessUrl;

    /**
     * 备注
     */
    private String description;
} 