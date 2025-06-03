package com.ryu.blog.repository;

import com.ryu.blog.entity.MenuPermission;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 菜单权限关联仓库接口
 * 提供对菜单权限关联数据的访问操作
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-27
 */
@Repository
public interface MenuPermissionRepository extends R2dbcRepository<MenuPermission, Long> {
    
    /**
     * 根据菜单ID查询关联的权限ID列表
     * 
     * @param menuId 菜单ID
     * @return 权限ID列表
     */
    @Query("SELECT permission_id FROM t_menu_permissions WHERE menu_id = :menuId")
    Flux<Long> findPermissionIdsByMenuId(Long menuId);
    
    /**
     * 根据菜单ID删除所有权限关联
     * 
     * @param menuId 菜单ID
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_menu_permissions WHERE menu_id = :menuId")
    Mono<Void> deleteByMenuId(Long menuId);
    
    /**
     * 根据权限ID列表查询菜单ID列表
     * 
     * @param permissionIds 权限ID列表
     * @return 菜单ID列表
     */
    @Query("SELECT DISTINCT menu_id FROM t_menu_permissions WHERE permission_id IN (:permissionIds)")
    Flux<Long> findMenuIdsByPermissionIds(List<Long> permissionIds);
} 