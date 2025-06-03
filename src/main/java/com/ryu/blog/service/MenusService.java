package com.ryu.blog.service;

import cn.hutool.core.lang.tree.Tree;
import com.ryu.blog.dto.MenusSaveDTO;
import com.ryu.blog.dto.MenusUpdateDTO;
import com.ryu.blog.entity.Menus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 菜单服务接口
 * 负责菜单相关的业务逻辑操作
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-27
 */
public interface MenusService {

    /**
     * 获取菜单树形结构
     * 
     * @return 菜单树形结构
     */
    Mono<List<Tree<Long>>> getMenusByTree();

    /**
     * 获取所有菜单
     * 
     * @return 菜单列表
     */
    Flux<Menus> listAll();

    /**
     * 绑定权限到菜单
     * 
     * @param permissionIds 权限ID列表
     * @param menuId 菜单ID
     * @return 绑定结果
     */
    Mono<Void> bindPermissions(List<Long> permissionIds, Long menuId);

    /**
     * 获取用户有权限访问的菜单
     * 
     * @param permissionIdentities 权限标识列表
     * @return 菜单列表
     */
    Flux<Menus> getUserMenus(List<String> permissionIdentities);

    /**
     * 保存菜单
     * 
     * @param menusSaveDTO 菜单保存DTO
     * @return 保存结果
     */
    Mono<Void> saveMenu(MenusSaveDTO menusSaveDTO);

    /**
     * 更新菜单
     * 
     * @param menusUpdateDTO 菜单更新DTO
     * @return 更新结果
     */
    Mono<Void> updateMenu(MenusUpdateDTO menusUpdateDTO);

    /**
     * 删除菜单
     * 
     * @param id 菜单ID
     * @return 删除结果
     */
    Mono<Void> deleteMenu(Long id);
} 