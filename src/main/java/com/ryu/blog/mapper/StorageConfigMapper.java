package com.ryu.blog.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.dto.StorageConfigCreateDTO;
import com.ryu.blog.dto.StorageConfigUpdateDTO;
import com.ryu.blog.entity.StorageConfig;
import org.mapstruct.*;

import java.time.LocalDateTime;

/**
 * 存储策略实体与DTO转换工具类
 *
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
public interface StorageConfigMapper {

    /**
     * 将创建DTO转换为实体
     *
     * @param dto 创建DTO
     * @return 实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "config", ignore = true) // 配置项通过自定义方法处理
    @Mapping(target = "maxFileSize", constant = "0L")
    @Mapping(target = "defaultExpiry", constant = "0L")
    @Mapping(target = "creatorId", constant = "0L")
    @Mapping(target = "createTime", expression = "java(LocalDateTime.now())")
    @Mapping(target = "updateTime", expression = "java(LocalDateTime.now())")
    @Mapping(target = "isDeleted", constant = "0")
    StorageConfig toEntity(StorageConfigCreateDTO dto);

    /**
     * 将更新DTO转换为实体
     *
     * @param dto 更新DTO
     * @return 实体
     */
    @Mapping(target = "config", ignore = true) // 配置项通过自定义方法处理
    @Mapping(target = "maxFileSize", constant = "0L")
    @Mapping(target = "defaultExpiry", constant = "0L")
    @Mapping(target = "creatorId", constant = "0L")
    @Mapping(target = "updateTime", expression = "java(LocalDateTime.now())")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    StorageConfig toEntity(StorageConfigUpdateDTO dto);
    
    /**
     * 更新实体的部分字段
     *
     * @param dto 更新DTO
     * @param entity 目标实体
     */
    @Mapping(target = "config", ignore = true) // 配置项通过自定义方法处理
    @Mapping(target = "updateTime", expression = "java(LocalDateTime.now())")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "maxFileSize", ignore = true)
    @Mapping(target = "defaultExpiry", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    void updateEntityFromDto(StorageConfigUpdateDTO dto, @MappingTarget StorageConfig entity);
    
    /**
     * 在映射完成后处理配置项
     *
     * @param dto    源对象
     * @param entity 目标对象
     */
    @AfterMapping
    default void handleConfig(StorageConfigCreateDTO dto, @MappingTarget StorageConfig entity) {
        if (dto.getConfig() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                entity.setConfig(mapper.writeValueAsString(dto.getConfig()));
            } catch (JsonProcessingException e) {
                System.err.println("序列化配置JSON失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 在映射完成后处理配置项
     *
     * @param dto    源对象
     * @param entity 目标对象
     */
    @AfterMapping
    default void handleConfig(StorageConfigUpdateDTO dto, @MappingTarget StorageConfig entity) {
        if (dto.getConfig() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                entity.setConfig(mapper.writeValueAsString(dto.getConfig()));
            } catch (JsonProcessingException e) {
                System.err.println("序列化配置JSON失败: " + e.getMessage());
            }
        }
    }
} 