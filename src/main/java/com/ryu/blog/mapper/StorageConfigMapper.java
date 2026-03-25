package com.ryu.blog.mapper;

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

    @Named("mapIsEnable")
    default Boolean mapIsEnable(Integer value) {
        return value != null && value == 1;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "maxFileSize", constant = "0L")
    @Mapping(target = "defaultExpiry", constant = "0L")
    @Mapping(target = "creatorId", constant = "0L")
    @Mapping(target = "createTime", expression = "java(LocalDateTime.now())")
    @Mapping(target = "updateTime", expression = "java(LocalDateTime.now())")
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "isEnable", qualifiedByName = "mapIsEnable")
    StorageConfig toEntity(StorageConfigCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "maxFileSize", constant = "0L")
    @Mapping(target = "defaultExpiry", constant = "0L")
    @Mapping(target = "creatorId", constant = "0L")
    @Mapping(target = "updateTime", expression = "java(LocalDateTime.now())")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "isEnable", qualifiedByName = "mapIsEnable")
    StorageConfig toEntity(StorageConfigUpdateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "maxFileSize", ignore = true)
    @Mapping(target = "defaultExpiry", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", expression = "java(LocalDateTime.now())")
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "isEnable", qualifiedByName = "mapIsEnable")
    void updateEntityFromDto(StorageConfigUpdateDTO dto, @MappingTarget StorageConfig entity);

    default StorageConfig toEntityWithConfig(StorageConfigCreateDTO dto) {
        if (dto == null) {
            return null;
        }
        StorageConfig entity = toEntity(dto);
        if (entity != null && dto.getConfig() != null) {
            entity.setConfigMap(dto.getConfig());
        }
        return entity;
    }

    default StorageConfig toEntityWithConfig(StorageConfigUpdateDTO dto) {
        if (dto == null) {
            return null;
        }
        StorageConfig entity = toEntity(dto);
        if (entity != null && dto.getConfig() != null) {
            entity.setConfigMap(dto.getConfig());
        }
        return entity;
    }

    default void updateEntityWithConfig(StorageConfigUpdateDTO dto, @MappingTarget StorageConfig entity) {
        updateEntityFromDto(dto, entity);
        if (dto.getConfig() != null) {
            entity.setConfigMap(dto.getConfig());
        }
    }
}