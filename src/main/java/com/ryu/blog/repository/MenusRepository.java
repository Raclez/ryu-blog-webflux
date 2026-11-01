package com.ryu.blog.repository;

import com.ryu.blog.entity.Menus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
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
     * 查询所有未删除的菜单，按父ID、排序字段、ID 升序排列
     *
     * <p>排序加了 {@code id} 兜底：只按 {@code parent_id, sort} 排序时，
     * 同 sort 的菜单顺序由 MySQL 自由决定，翻页会出现重复或漏项。
     *
     * @return 菜单列表
     */
    @Query("SELECT * FROM t_menus WHERE is_deleted = 0 ORDER BY parent_id, sort, id")
    Flux<Menus> findAllOrderByParentIdAndSort();

    /**
     * 路由名是否已存在
     *
     * <p>查的是全表（不过滤 is_deleted）：{@code uk_menus_route_name} 是唯一索引，
     * 软删的行同样占着这个值，只查未删除会漏掉、最后报 Duplicate entry。
     *
     * @param routeName 路由名
     * @return 是否存在
     */
    Mono<Boolean> existsByRouteName(String routeName);

    /**
     * 路由名是否已被<b>其它</b>菜单占用
     *
     * @param routeName 路由名
     * @param id        当前菜单ID
     * @return 是否存在
     */
    Mono<Boolean> existsByRouteNameAndIdNot(String routeName, Long id);

    /**
     * 根据ID列表查询未删除的菜单
     *
     * @param ids 菜单ID列表
     * @return 菜单列表
     */
    @Query("SELECT * FROM t_menus WHERE id IN (:ids) AND is_deleted = 0 ORDER BY parent_id, sort, id")
    Flux<Menus> findByIdIn(List<Long> ids);

    /**
     * 根据父ID查询子菜单数量（只统计未删除的）
     *
     * @param parentId 父菜单ID
     * @return 子菜单数量
     */
    @Query("SELECT COUNT(*) FROM t_menus WHERE parent_id = :parentId AND is_deleted = 0")
    Mono<Long> countByParentId(Long parentId);

    /**
     * 根据父ID查询未删除的子菜单
     *
     * @param parentId 父菜单ID
     * @return 子菜单列表
     */
    @Query("SELECT * FROM t_menus WHERE parent_id = :parentId AND is_deleted = 0 ORDER BY sort, id")
    Flux<Menus> findByParentId(Long parentId);
}
