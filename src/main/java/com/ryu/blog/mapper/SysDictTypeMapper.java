package com.ryu.blog.mapper;

import com.ryu.blog.dto.SysDictTypeAddDTO;
import com.ryu.blog.dto.SysDictTypeUpdateDTO;
import com.ryu.blog.entity.SysDictType;
import com.ryu.blog.vo.SysDictTypeVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 字典类型数据转换器
 *
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDictTypeMapper {

    /**
     * 实体转VO
     *
     * @param entity 实体
     * @return VO
     */
    SysDictTypeVO toVO(SysDictType entity);

    /**
     * 实体列表转VO列表
     *
     * @param entities 实体列表
     * @return VO列表
     */
    List<SysDictTypeVO> toVOList(List<SysDictType> entities);

    /**
     * 添加DTO转实体
     *
     * @param dto 添加DTO
     * @return 实体
     */
    SysDictType toEntity(SysDictTypeAddDTO dto);

    /**
     * 更新DTO转实体
     *
     * @param dto 更新DTO
     * @return 实体
     */
    SysDictType toEntity(SysDictTypeUpdateDTO dto);

    /**
     * 更新实体
     *
     * @param dto 更新DTO
     * @param entity 要更新的实体
     * @return 更新后的实体
     */
    SysDictType updateEntity(SysDictTypeUpdateDTO dto, @MappingTarget SysDictType entity);
} 