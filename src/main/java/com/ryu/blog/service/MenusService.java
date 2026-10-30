package com.ryu.blog.service;

import cn.hutool.core.lang.tree.Tree;
import com.ryu.blog.dto.MenusSaveDTO;
import com.ryu.blog.dto.MenusUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.Menus;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 菜单服务接口
 *
 * <p><b>可见性模型</b>：{@code user → t_user_roles → t_sys_role_menus → t_menus}
 * —— 角色直接挂菜单（role → menu 直连）。
 * 「能不能做某个操作」由 {@code t_role_permissions} 单独决定，两者是互补关系：
 * 看得见菜单 ≠ 能做菜单里的操作。
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-27
 */
public interface MenusService {

    /**
     * 获取全部菜单的树形结构（管理端视图，不做权限过滤）
     *
     * @return 菜单树
     */
    Mono<List<Tree<Long>>> getMenusByTree();

    /**
     * 获取全部菜单（平铺）
     *
     * @return 菜单列表
     */
    Mono<List<Menus>> listAll();

    /**
     * 获取某个用户可见的菜单树
     *
     * <p>权限标识与可见菜单<b>全部由服务端推导</b>，不接受客户端传入 ——
     * 原先的 {@code POST /menus/user} 直接收客户端传来的权限标识，
     * 等于让客户端自己证明自己有权限。
     *
     * @param userId 用户ID
     * @return 该用户可见的菜单树（含自动补齐的祖先目录）
     */
    Mono<List<Tree<Long>>> getUserMenusTree(Long userId);

    /**
     * 为菜单分配角色（决定哪些角色能看到该菜单）
     *
     * @param ctx    操作者上下文
     * @param roleIds 角色ID列表
     * @param menuId 菜单ID
     */
    Mono<Void> bindRoles(OperatorContext ctx, List<Long> roleIds, Long menuId);

    /**
     * 查询某个菜单当前绑定了哪些角色
     *
     * <p>与 {@link #bindRoles} 成对使用 —— 绑之前先读回显，
     * 否则管理端只能「写」不能「看」，绑定关系对管理员不可见。
     *
     * @param menuId 菜单ID
     * @return 角色ID列表（未绑定任何角色时返回空列表）
     */
    Mono<List<Long>> getRoleIdsByMenu(Long menuId);

    /**
     * 新建菜单
     *
     * @param ctx         操作者上下文
     * @param menusSaveDTO 菜单信息
     */
    Mono<Void> saveMenu(OperatorContext ctx, MenusSaveDTO menusSaveDTO);

    /**
     * 更新菜单
     *
     * @param ctx            操作者上下文
     * @param menusUpdateDTO 菜单信息
     */
    Mono<Void> updateMenu(OperatorContext ctx, MenusUpdateDTO menusUpdateDTO);

    /**
     * 删除菜单
     *
     * @param ctx 操作者上下文
     * @param id  菜单ID
     */
    Mono<Void> deleteMenu(OperatorContext ctx, Long id);
}
