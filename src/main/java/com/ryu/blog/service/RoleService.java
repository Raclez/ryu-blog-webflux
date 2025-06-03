package com.ryu.blog.service;

import com.ryu.blog.dto.PermissionsAssignDTO;
import com.ryu.blog.dto.RoleDTO;
import com.ryu.blog.dto.RoleListDTO;
import com.ryu.blog.entity.Role;
import com.ryu.blog.vo.RolePermissionsVO;
import com.ryu.blog.vo.RoleVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 角色服务接口
 * 负责角色相关的业务逻辑操作
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
public interface RoleService {

    /**
     * 保存角色
     * 
     * @param roleDTO 角色DTO
     * @return 保存结果
     */
    Mono<Void> saveRole(RoleDTO roleDTO);

    /**
     * 为角色分配权限
     * 
     * @param permissionsAssignDTO 权限分配DTO
     * @return 分配结果
     */
    Mono<Void> assignPermissions(PermissionsAssignDTO permissionsAssignDTO);

    /**
     * 根据条件查询角色列表
     * 
     * @param roleListDTO 查询条件
     * @return 角色分页列表
     */
    Flux<Role> getRoles(RoleListDTO roleListDTO);

    /**
     * 获取角色详情
     * 
     * @param id 角色ID
     * @return 角色详情VO
     */
    Mono<RoleVO> getRoleDetails(Long id);

    /**
     * 删除角色权限
     * 
     * @param permissionsAssignDTO 权限分配DTO
     * @return 删除结果
     */
    Mono<Void> removePermission(PermissionsAssignDTO permissionsAssignDTO);

    /**
     * 获取角色权限
     * 
     * @param id 角色ID
     * @return 角色权限VO
     */
    Mono<RolePermissionsVO> getRolePermissions(Long id);

    /**
     * 获取默认角色
     * 
     * @return 默认角色
     */
    Mono<Role> getDefaultRole();

    /**
     * 获取用户角色
     * 
     * @param userId 用户ID
     * @return 角色列表
     */
    Flux<Role> getUserRoles(Long userId);

    /**
     * 变更角色状态
     * 
     * @param roleId 角色ID
     * @param isActive 激活状态
     * @return 操作结果
     */
    Mono<Boolean> changeRoleStatus(Long roleId, Integer isActive);

    /**
     * 批量分配用户角色
     * 
     * @param userIds 用户ID列表
     * @param roleId 角色ID
     * @param assignBy 分配人
     * @return 操作结果
     */
    Mono<Boolean> batchAssignUserRoles(List<Long> userIds, Long roleId, String assignBy);

    /**
     * 获取所有角色
     * 
     * @return 角色列表
     */
    Flux<Role> getAllRoles();
    
    /**
     * 根据ID删除角色
     * 
     * @param id 角色ID
     * @return 删除结果
     */
    Mono<Void> removeById(Long id);
} 