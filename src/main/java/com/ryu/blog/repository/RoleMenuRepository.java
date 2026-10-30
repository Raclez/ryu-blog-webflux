package com.ryu.blog.repository;

import com.ryu.blog.entity.RoleMenu;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 角色-菜单关联仓储
 *
 * <p>菜单可见性模型：{@code user → t_user_roles → t_sys_role_menus → t_menus}。
 *
 * @author ryu
 * @since 1.0.0
 */
@Repository
public interface RoleMenuRepository extends R2dbcRepository<RoleMenu, Long> {

    /**
     * 查询一批角色可见的菜单ID
     *
     * @param roleIds 角色ID列表
     * @return 菜单ID列表（已去重）
     */
    @Query("SELECT DISTINCT menu_id FROM t_sys_role_menus WHERE role_id IN (:roleIds) AND is_deleted = 0")
    Flux<Long> findMenuIdsByRoleIds(List<Long> roleIds);

    /**
     * 查询拥有某个菜单的角色ID
     *
     * @param menuId 菜单ID
     * @return 角色ID列表
     */
    @Query("SELECT DISTINCT role_id FROM t_sys_role_menus WHERE menu_id = :menuId AND is_deleted = 0")
    Flux<Long> findRoleIdsByMenuId(Long menuId);

    /**
     * 删除某个菜单的全部角色关联
     *
     * @param menuId 菜单ID
     */
    @Modifying
    @Query("DELETE FROM t_sys_role_menus WHERE menu_id = :menuId")
    Mono<Void> deleteByMenuId(Long menuId);

    /**
     * 删除某个角色与某个菜单的关联
     *
     * @param roleId 角色ID
     * @param menuId 菜单ID
     */
    @Modifying
    @Query("DELETE FROM t_sys_role_menus WHERE role_id = :roleId AND menu_id = :menuId")
    Mono<Void> deleteByRoleIdAndMenuId(Long roleId, Long menuId);

    /**
     * 删除某个角色的全部菜单关联
     *
     * @param roleId 角色ID
     */
    @Modifying
    @Query("DELETE FROM t_sys_role_menus WHERE role_id = :roleId")
    Mono<Void> deleteByRoleId(Long roleId);
}
