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
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDictItemMapper {

    @Mapping(target = "dictType", ignore = true)
    @Mapping(target = "typeName", ignore = true)
    SysDictItemVO toVO(SysDictItem entity);

    List<SysDictItemVO> toVOList(List<SysDictItem> entities);

    /**
     * 查询DTO转实体
     *
     * <p>SysDictItemDTO 是查询条件 DTO，本身不含实体的业务字段，
     * 这里只做「查得到什么就映射什么」，其余字段显式忽略。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "dictItemKey", ignore = true)
    @Mapping(target = "dictItemValue", ignore = true)
    @Mapping(target = "sort", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "remark", ignore = true)
    SysDictItem toEntity(SysDictItemDTO dto);

    /**
     * 保存DTO转实体
     *
     * <p>id / createTime / updateTime / isDeleted 由服务端掌控，显式忽略。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    SysDictItem toEntity(SysDictItemSaveDTO dto);

    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    SysDictItem toEntity(SysDictItemUpdateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "dictItemKey", ignore = true)
    @Mapping(target = "dictItemValue", ignore = true)
    @Mapping(target = "sort", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "remark", ignore = true)
    SysDictItem updateEntity(SysDictItemDTO dto, @MappingTarget SysDictItem entity);

    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
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
