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
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {
    
    /**
     * 将User实体转换为UserVO
     *
     * @param user 用户实体
     * @return UserVO
     */
    @Mapping(target = "lastLogin", source = "lastLoginTime")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "roleIds", ignore = true)
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
    // 多个源参数时，source 必须写成「参数名.属性名」
    @Mapping(target = "lastLogin", source = "user.lastLoginTime")
    UserVO toUserVO(User user, List<Role> roles);

    /**
     * 将User实体转换为UserInfoVO
     *
     * @param user 用户实体
     * @return UserInfoVO
     */
    @Mapping(target = "lastLogin", source = "lastLoginTime")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    UserInfoVO toUserInfoVO(User user);

    /**
     * 将User实体和角色列表转换为UserInfoVO
     *
     * @param user 用户实体
     * @param roles 角色列表
     * @return UserInfoVO
     */
    @Mapping(target = "roles", expression = "java(rolesToRoleVOs(roles))")
    // 多个源参数时，source 必须写成「参数名.属性名」
    @Mapping(target = "lastLogin", source = "user.lastLoginTime")
    @Mapping(target = "permissions", ignore = true)
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
    // 账号状态由专用的改状态接口控制（独立的 system:user:status 权限），
    // 登录痕迹由服务端写 —— 这两个字段都不能从请求体带进来
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lastLoginIp", ignore = true)
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
    // 「改资料」不等于「改账号状态」——状态走 PUT /users/{id}/status/{status}
    @Mapping(target = "status", ignore = true)
    // 登录 IP 由服务端在登录成功时写死，客户端永远改不了
    @Mapping(target = "lastLoginIp", ignore = true)
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