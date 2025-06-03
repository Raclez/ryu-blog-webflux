package com.ryu.blog.repository;

import com.ryu.blog.entity.Menus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 菜单仓库接口
 * 提供对菜单数据的访问操作
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-27
 */
@Repository
public interface MenusRepository extends R2dbcRepository<Menus, Long> {

    /**
     * 根据父ID查询菜单列表，按排序字段升序排列
     *
     * @param parentId 父菜单ID
     * @return 菜单列表
     */
    Flux<Menus> findByParentIdOrderBySortAsc(Long parentId);

    /**
     * 查询所有菜单，按父ID和排序字段升序排列
     *
     * @return 菜单列表
     */
    @Query("SELECT * FROM t_menus ORDER BY parent_id, sort")
    Flux<Menus> findAllOrderByParentIdAndSort();

    /**
     * 根据ID列表查询菜单
     *
     * @param ids 菜单ID列表
     * @return 菜单列表
     */
    Flux<Menus> findByIdIn(List<Long> ids);

    /**
     * 根据父ID查询子菜单数量
     *
     * @param parentId 父菜单ID
     * @return 子菜单数量
     */
    Mono<Long> countByParentId(Long parentId);

    /**
     * 根据权限标识列表查询菜单
     *
     * @param permissionIdentities 权限标识列表
     * @return 菜单列表
     */
    @Query("SELECT m.* FROM t_menus m JOIN t_menu_permissions mp ON m.id = mp.menu_id JOIN t_permissions p ON mp.permission_id = p.id WHERE p.identity IN (:permissionIdentities)")
    Flux<Menus> findByPermissionIdentitiesIn(List<String> permissionIdentities);
} 