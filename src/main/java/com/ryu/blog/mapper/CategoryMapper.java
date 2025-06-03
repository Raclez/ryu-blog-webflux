package com.ryu.blog.mapper;

import com.ryu.blog.dto.CategoryCreateDTO;
import com.ryu.blog.dto.CategoryUpdateDTO;
import com.ryu.blog.entity.Category;
import com.ryu.blog.vo.CategoryStatsVO;
import com.ryu.blog.vo.CategoryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * 分类对象映射器
 * 负责DTO、Entity和VO之间的转换
 *
 * @author ryu
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CategoryMapper {

    /**
     * 将创建DTO转换为实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "articleCount", ignore = true)
    @Mapping(target = "sort", source = "sort", qualifiedByName = "longToInteger")
    Category toEntity(CategoryCreateDTO dto);

    /**
     * 将更新DTO转换为实体
     */
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "articleCount", ignore = true)
    @Mapping(target = "sort", source = "sort", qualifiedByName = "longToInteger")
    Category toEntity(CategoryUpdateDTO dto);

    /**
     * 将实体转换为VO
     */
    CategoryVO toVO(Category entity);

    /**
     * 将实体转换为统计VO
     */
    @Mapping(target = "postCount", source = "articleCount")
    CategoryStatsVO toStatsVO(Category entity);
    
    /**
     * 更新实体属性
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "articleCount", ignore = true)
    void updateEntityFromDTO(CategoryUpdateDTO dto, @MappingTarget Category entity);
    
    /**
     * Long转Integer的转换方法
     */
    @Named("longToInteger")
    default Integer longToInteger(Long value) {
        return value != null ? value.intValue() : null;
    }
} 