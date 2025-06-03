package com.ryu.blog.dto;

import lombok.Data;

import java.util.Map;

/**
 * 存储策略更新DTO
 *
 * @author ryu 475118582@qq.com
 */
@Data
public class StorageConfigUpdateDTO {

    /**
     * 存储策略ID
     */
    private Long id;

    /**
     * 策略名称
     */
    private String strategyName;

    /**
     * 策略标识，如 local, oss, minio
     */
    private String strategyKey;

    /**
     * 存储服务的配置信息
     */
    private Map<String, String> config;

    /**
     * 是否启用此策略
     */
    private Boolean isEnable;


    private  String accessUrl;

    /**
     * 备注
     */
    private String description;
} 