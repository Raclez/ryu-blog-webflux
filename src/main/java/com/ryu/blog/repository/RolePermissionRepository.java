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

    @Query("SELECT role_id, permission_id FROM t_role_permissions WHERE role_id IN (:roleIds)")
    Flux<RolePermission> findByRoleIds(List<Long> roleIds);

    /**
     * 查询用户（经由启用中的角色）直接拥有的权限ID
     *
     * <p>这是权限判定的<b>唯一权威链路</b>：user → role → permission。
     * 相比原先「先查 roleIds 再查 role_permissions 再查 permissions」的三段查询，
     * 这里一次 JOIN 走完，并且把三处过滤条件都写进了 SQL：
     * <ul>
     *   <li>{@code r.is_active = 1} —— 禁用角色不再授予权限</li>
     *   <li>{@code r.is_deleted = 0} —— 已删除角色不授予权限</li>
     *   <li>{@code rp.is_deleted = 0} —— 已删除的授权关系不生效</li>
     * </ul>
     *
     * <p>权限自身的 {@code is_active} 在 {@link PermissionsRepository#findEnabledByIdIn} 里过滤。
     *
     * @param userId 用户ID
     * @return 权限ID列表（已去重）
     */
    @Query("SELECT DISTINCT rp.permission_id FROM t_role_permissions rp "
            + "JOIN t_roles r ON r.id = rp.role_id "
            + "JOIN t_user_roles ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = :userId AND r.is_active = 1 AND r.is_deleted = 0 AND rp.is_deleted = 0")
    Flux<Long> findPermissionIdsByUserId(Long userId);
} 