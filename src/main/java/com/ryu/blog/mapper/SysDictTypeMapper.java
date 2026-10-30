package com.ryu.blog.mapper;

import com.ryu.blog.dto.SysDictTypeAddDTO;
import com.ryu.blog.dto.SysDictTypeUpdateDTO;
import com.ryu.blog.entity.SysDictType;
import com.ryu.blog.vo.SysDictTypeVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 字典类型数据转换器
 *
 * @author ryu 475118582@qq.com
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = MappingConstants.ComponentModel.SPRING)
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
     * <p>id / createTime / updateTime / isDeleted 由服务端掌控，
     * 显式忽略，避免客户端通过 DTO 越权写入（mass assignment）。
     *
     * @param dto 添加DTO
     * @return 实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    SysDictType toEntity(SysDictTypeAddDTO dto);

    /**
     * 更新DTO转实体
     *
     * @param dto 更新DTO
     * @return 实体
     */
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    SysDictType toEntity(SysDictTypeUpdateDTO dto);

    /**
     * 更新实体
     *
     * @param dto 更新DTO
     * @param entity 现有实体
     * @return 更新后的实体
     */
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    SysDictType updateEntity(SysDictTypeUpdateDTO dto, @MappingTarget SysDictType entity);
}
