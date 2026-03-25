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

    @Mapping(target = "dictType", ignore = true)
    @Mapping(target = "typeName", ignore = true)
    SysDictItemVO toVO(SysDictItem entity);

    List<SysDictItemVO> toVOList(List<SysDictItem> entities);

    SysDictItem toEntity(SysDictItemDTO dto);

    SysDictItem toEntity(SysDictItemSaveDTO dto);

    SysDictItem toEntity(SysDictItemUpdateDTO dto);

    SysDictItem updateEntity(SysDictItemDTO dto, @MappingTarget SysDictItem entity);

    SysDictItem updateEntity(SysDictItemUpdateDTO dto, @MappingTarget SysDictItem entity);

    default SysDictItemVO toVOWithDictType(SysDictItem entity, SysDictType dictType) {
        SysDictItemVO vo = toVO(entity);
        if (vo != null && dictType != null) {
            vo.setDictType(dictType.getDictType());
            vo.setTypeName(dictType.getTypeName());
        }
        return vo;
    }
}