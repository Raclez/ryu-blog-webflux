package com.ryu.blog.mapper;

import com.ryu.blog.dto.TagCreateDTO;
import com.ryu.blog.dto.TagUpdateDTO;
import com.ryu.blog.entity.Tag;
import com.ryu.blog.vo.TagVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 标签实体映射器
 * 
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TagMapper {
    
    /**
     * 将Tag实体转换为TagVO
     * 
     * @param tag 标签实体
     * @return TagVO
     */
    TagVO toTagVO(Tag tag);
    
    /**
     * 将标签列表转换为TagVO列表
     * 
     * @param tags 标签列表
     * @return TagVO列表
     */
    List<TagVO> toTagVOList(List<Tag> tags);
    
    /**
     * 将Tag实体转换为带文章数量的TagVO
     * 
     * @param tag 标签实体
     * @param articleCount 文章数量
     * @return TagVO
     */
    @Mapping(target = "articleCount", source = "articleCount")
    TagVO toTagVOWithArticleCount(Tag tag, Long articleCount);
    
    /**
     * 将TagCreateDTO转换为Tag实体
     * 
     * @param tagCreateDTO 标签创建DTO
     * @return Tag
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Tag toTag(TagCreateDTO tagCreateDTO);
    
    /**
     * 根据TagUpdateDTO更新Tag实体
     * 
     * @param tagUpdateDTO 标签更新DTO
     * @param tag 原标签实体
     * @return 更新后的Tag
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Tag updateTagFromDTO(TagUpdateDTO tagUpdateDTO, @MappingTarget Tag tag);
} 