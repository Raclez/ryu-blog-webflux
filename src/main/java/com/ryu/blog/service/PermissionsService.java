package com.ryu.blog.service;

import com.ryu.blog.dto.PermissionsAddDTO;
import com.ryu.blog.dto.PermissionsQueryDTO;
import com.ryu.blog.dto.PermissionsUpdateDTO;
import com.ryu.blog.entity.Permissions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * 权限服务接口
 * 负责权限相关的业务逻辑操作
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
public interface PermissionsService {

    /**
     * 获取所有权限
     * 
     * @return 权限列表
     */
    Flux<Permissions> list();

    /**
     * 更新权限
     * 
     * @param permissionsUpdateDTO 权限更新DTO
     * @return 更新结果
     */
    Mono<Void> updatePermission(PermissionsUpdateDTO permissionsUpdateDTO);

    /**
     * 按模块获取权限列表
     * 
     * @param module 模块名称
     * @return 权限列表
     */
    Flux<Permissions> getPermissionsByModule(String module);

    /**
     * 分页查询权限列表
     * 
     * @param permissionsQuery 查询条件
     * @return 权限分页列表
     */
    Flux<Permissions> getPermissionsByPage(PermissionsQueryDTO permissionsQuery);

    /**
     * 保存权限
     * 
     * @param permissionsAddDTO 权限添加DTO
     * @return 保存结果
     */
    Mono<Void> savePermission(PermissionsAddDTO permissionsAddDTO);

    /**
     * 根据ID删除权限
     * 
     * @param id 权限ID
     * @return 删除结果
     */
    Mono<Void> removeById(Long id);

    /**
     * 获取用户权限标识
     * 
     * @param userId 用户ID
     * @return 权限标识集合
     */
    Mono<Set<String>> getUserPermissionIdentities(Long userId);

    /**
     * 获取所有模块
     * 
     * @return 模块列表
     */
    Mono<List<String>> getAllModules();

    /**
     * 检查用户是否拥有指定权限
     * 
     * @param userId 用户ID
     * @param permissionIdentity 权限标识
     * @return 是否拥有权限
     */
    Mono<Boolean> hasPermission(Long userId, String permissionIdentity);

    /**
     * 获取角色权限列表
     * 
     * @param roleId 角色ID
     * @return 权限列表
     */
    Flux<Permissions> getPermissionsByRoleId(Long roleId);

    /**
     * 获取菜单权限列表
     * 
     * @param menuId 菜单ID
     * @return 权限列表
     */
    Flux<Permissions> getPermissionsByMenuId(Long menuId);

    /**
     * 批量更新权限状态
     * 
     * @param ids 权限ID列表
     * @param isActive 激活状态
     * @return 操作结果
     */
    Mono<Boolean> updatePermissionStatus(List<Long> ids, Integer isActive);
} 