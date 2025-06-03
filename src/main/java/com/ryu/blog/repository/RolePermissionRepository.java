package com.ryu.blog.repository;

import com.ryu.blog.entity.RolePermission;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 角色权限关联仓库接口
 * 提供对角色权限关联数据的访问操作
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Repository
public interface RolePermissionRepository extends R2dbcRepository<RolePermission, Long> {
    
    /**
     * 根据角色ID查询关联的权限ID列表
     * 
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    @Query("SELECT permission_id FROM t_role_permissions WHERE role_id = :roleId")
    Flux<Long> findPermissionIdsByRoleId(Long roleId);
    
    /**
     * 根据角色ID删除所有权限关联
     * 
     * @param roleId 角色ID
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_role_permissions WHERE role_id = :roleId")
    Mono<Void> deleteByRoleId(Long roleId);
    
    /**
     * 根据角色ID和权限ID列表删除权限关联
     * 
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_role_permissions WHERE role_id = :roleId AND permission_id IN (:permissionIds)")
    Mono<Void> deleteByRoleIdAndPermissionIdIn(Long roleId, List<Long> permissionIds);
    
    /**
     * 根据权限ID查询关联的角色ID列表
     * 
     * @param permissionId 权限ID
     * @return 角色ID列表
     */
    @Query("SELECT role_id FROM t_role_permissions WHERE permission_id = :permissionId")
    Flux<Long> findRoleIdsByPermissionId(Long permissionId);
} 