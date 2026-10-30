package com.ryu.blog.mapper;

import com.ryu.blog.dto.ResourceGroupCreateDTO;
import com.ryu.blog.dto.ResourceGroupUpdateDTO;
import com.ryu.blog.entity.ResourceGroup;
import com.ryu.blog.vo.ResourceGroupVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * 资源组对象映射接口
 *
 * @author ryu
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ResourceGroupMapper {

    /**
     * 将创建DTO转换为实体
     *
     * <p>id / createTime / updateTime / isDeleted / creatorId 由服务端掌控，显式忽略
     * （creatorId 取当前登录用户，不接受客户端指定）。
     *
     * @param dto 创建DTO
     * @return 实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    ResourceGroup toEntity(ResourceGroupCreateDTO dto);

    /**
     * 使用更新DTO更新实体
     *
     * @param dto 更新DTO
     * @param entity 要更新的实体
     */
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "sort", ignore = true)
    void updateEntityFromDTO(ResourceGroupUpdateDTO dto, @MappingTarget ResourceGroup entity);

    /**
     * 将实体转换为简单VO
     *
     * @param entity 实体
     * @return 视图对象
     */
    @Mapping(source = "id", target = "id")
    ResourceGroupVO toVO(ResourceGroup entity);
} 