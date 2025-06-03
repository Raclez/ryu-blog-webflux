package com.ryu.blog.mapper;

import com.ryu.blog.dto.SysDictItemDTO;
import com.ryu.blog.dto.SysDictItemSaveDTO;
import com.ryu.blog.dto.SysDictItemUpdateDTO;
import com.ryu.blog.entity.SysDictItem;
import com.ryu.blog.entity.SysDictType;
import com.ryu.blog.vo.SysDictItemVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 字典项数据转换器
 *
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDictItemMapper {

    /**
     * 实体转VO
     *
     * @param entity 实体
     * @return VO
     */
    SysDictItemVO toVO(SysDictItem entity);

    /**
     * 实体列表转VO列表
     *
     * @param entities 实体列表
     * @return VO列表
     */
    List<SysDictItemVO> toVOList(List<SysDictItem> entities);

    /**
     * DTO转实体
     *
     * @param dto DTO
     * @return 实体
     */
    SysDictItem toEntity(SysDictItemDTO dto);
    
    /**
     * SaveDTO转实体
     *
     * @param dto SaveDTO
     * @return 实体
     */
    SysDictItem toEntity(SysDictItemSaveDTO dto);
    
    /**
     * UpdateDTO转实体
     *
     * @param dto UpdateDTO
     * @return 实体
     */
    SysDictItem toEntity(SysDictItemUpdateDTO dto);

    /**
     * 更新实体
     *
     * @param dto 数据传输对象
     * @param entity 要更新的实体
     * @return 更新后的实体
     */
    SysDictItem updateEntity(SysDictItemDTO dto, @MappingTarget SysDictItem entity);
    
    /**
     * 使用UpdateDTO更新实体
     *
     * @param dto 更新数据传输对象
     * @param entity 要更新的实体
     * @return 更新后的实体
     */
    SysDictItem updateEntity(SysDictItemUpdateDTO dto, @MappingTarget SysDictItem entity);
    
    /**
     * 设置字典项的字典类型信息
     *
     * @param dictItemVO 字典项VO
     * @param dictType 字典类型
     */
    @AfterMapping
    default void setDictTypeInfo(@MappingTarget SysDictItemVO dictItemVO, SysDictType dictType) {
        if (dictType != null) {
            dictItemVO.setDictType(dictType.getDictType());
            dictItemVO.setTypeName(dictType.getTypeName());
        }
    }
} 