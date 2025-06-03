package com.ryu.blog.mapper;

import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.vo.SysConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 系统配置数据转换器
 *
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysConfigMapper {

    SysConfigMapper INSTANCE = Mappers.getMapper(SysConfigMapper.class);

    /**
     * 实体转VO
     *
     * @param entity 实体
     * @return VO
     */
    SysConfigVO toVO(SysConfig entity);


    SysConfig toEntity(SysConfigUpdateDTO dto);

    /**
     * 实体列表转VO列表
     *
     * @param entities 实体列表
     * @return VO列表
     */
    List<SysConfigVO> toVOList(List<SysConfig> entities);

    /**
     * DTO转实体
     *
     * @param dto DTO
     * @return 实体
     */
    SysConfig toEntity(SysConfigDTO dto);

    /**
     * 更新实体
     *
     * @param dto 数据传输对象
     * @param entity 要更新的实体
     * @return 更新后的实体
     */
    SysConfig updateEntity(SysConfigDTO dto, @MappingTarget SysConfig entity);
} 