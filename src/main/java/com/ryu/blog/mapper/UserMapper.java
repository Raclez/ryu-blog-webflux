package com.ryu.blog.mapper;

import com.ryu.blog.dto.UserDTO;
import com.ryu.blog.entity.Role;
import com.ryu.blog.entity.User;
import com.ryu.blog.vo.UserInfoVO;
import com.ryu.blog.vo.UserVO;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户实体映射器
 * 
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    
    /**
     * 将User实体转换为UserVO
     * 
     * @param user 用户实体
     * @return UserVO
     */
    UserVO toUserVO(User user);
    
    /**
     * 将User实体和角色列表转换为UserVO
     * 
     * @param user 用户实体
     * @param roles 角色列表
     * @return UserVO
     */
    @Mapping(target = "roles", expression = "java(rolesToNames(roles))")
    @Mapping(target = "roleIds", expression = "java(rolesToIds(roles))")
    UserVO toUserVO(User user, List<Role> roles);
    
    /**
     * 将User实体转换为UserInfoVO
     * 
     * @param user 用户实体
     * @return UserInfoVO
     */
    UserInfoVO toUserInfoVO(User user);
    
    /**
     * 将User实体和角色列表转换为UserInfoVO
     * 
     * @param user 用户实体
     * @param roles 角色列表
     * @return UserInfoVO
     */
    @Mapping(target = "roles", expression = "java(rolesToRoleVOs(roles))")
    UserInfoVO toUserInfoVO(User user, List<Role> roles);
    
    /**
     * 将UserDTO转换为User实体
     * 
     * @param userDTO 用户DTO
     * @return User
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "lastLoginTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    User toUser(UserDTO userDTO);
    
    /**
     * 更新User实体
     * 
     * @param userDTO 用户DTO
     * @param user 现有用户实体
     * @return User
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "lastLoginTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "password", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    User updateUserFromDTO(UserDTO userDTO, @MappingTarget User user);
    
    /**
     * 将角色列表转换为角色名称列表
     * 
     * @param roles 角色列表
     * @return 角色名称列表
     */
    default List<String> rolesToNames(List<Role> roles) {
        if (roles == null) {
            return null;
        }
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toList());
    }
    
    /**
     * 将角色列表转换为角色ID列表
     * 
     * @param roles 角色列表
     * @return 角色ID列表
     */
    default List<Long> rolesToIds(List<Role> roles) {
        if (roles == null) {
            return null;
        }
        return roles.stream()
                .map(Role::getId)
                .collect(Collectors.toList());
    }
    
    /**
     * 将角色列表转换为RoleVO列表
     * 
     * @param roles 角色列表
     * @return RoleVO列表
     */
    default List<UserInfoVO.RoleVO> rolesToRoleVOs(List<Role> roles) {
        if (roles == null) {
            return null;
        }
        return roles.stream()
                .map(role -> new UserInfoVO.RoleVO(role.getId(), role.getName(), role.getCode()))
                .collect(Collectors.toList());
    }
} 