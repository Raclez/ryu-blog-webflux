package com.ryu.blog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.tree.Tree;
import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.dto.MenusSaveDTO;
import com.ryu.blog.dto.MenusUpdateDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.Menus;
import com.ryu.blog.service.MenusService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
* 系统菜单管理控制器
* 负责菜单的CRUD操作、菜单角色绑定和用户菜单获取等功能
*
* @author ryu 475118582@qq.com
* @since 1.0.0 2024-08-27
*/
@RestController
@RequestMapping("/menus")
@Tag(name="菜单管理接口")
@AllArgsConstructor
@Slf4j
public class MenusController {
    private final MenusService menusService;

    /**
     * 获取菜单树形结构
     * 按照层级关系构建树形菜单，用于前端导航展示
     *
     * @return 包含菜单树形结构的结果对象
     */
    @SaCheckPermission(PermissionConstants.MENU_VIEW)
    @GetMapping("/tree")
    @Operation(summary = "获取菜单树形结构", description = "获取系统菜单的树形结构")
    public Mono<Result<List<Tree<Long>>>> tree() {
        log.info("请求获取菜单树形结构");
        return menusService.getMenusByTree()
                .map(Result::success);
    }

    /**
     * 获取所有菜单
     * 返回系统中所有菜单的平铺列表，不含层级关系
     *
     * @return 包含所有菜单的结果对象
     */
    @SaCheckPermission(PermissionConstants.MENU_VIEW)
    @GetMapping("/all")
    @Operation(summary = "获取所有菜单", description = "获取系统中所有菜单的完整列表")
    public Mono<Result<List<Menus>>> getAllMenus() {
        log.info("请求获取所有菜单");
        return menusService.listAll()
                .map(Result::success);
    }

    /**
     * 为菜单分配角色
     *
     * <p>菜单可见性模型：<b>role → menu 直连</b>。
     * 分配了角色的菜单，该角色的用户才能在导航里看到它。
     *
     * @param roleIds 角色ID列表
     * @param menuId  菜单ID
     * @return 操作结果
     */
    @SaCheckPermission(PermissionConstants.MENU_ASSIGN)
    @PostMapping("/bindRoles")
    @Operation(summary = "为菜单分配角色", description = "分配哪些角色能看到该菜单")
    public Mono<Result<String>> bindRoles(
            @RequestBody @Parameter(description = "角色ID列表") List<Long> roleIds,
            @RequestParam @Parameter(description = "菜单ID") Long menuId) {
        log.info("请求为菜单分配角色, menuId: {}", menuId);

        return menusService.bindRoles(OperatorContext.current(), roleIds, menuId)
                .thenReturn(Result.success("分配角色成功"));
    }

    /**
     * 查询某个菜单绑定了哪些角色
     *
     * <p>与 {@code POST /menus/bindRoles} 成对使用：绑定前先读回显，
     * 否则管理端只能写不能看。
     *
     * @param menuId 菜单ID
     * @return 角色ID列表
     */
    @SaCheckPermission(PermissionConstants.MENU_VIEW)
    @GetMapping("/{menuId}/roles")
    @Operation(summary = "查询菜单绑定的角色", description = "返回可见该菜单的角色ID列表")
    public Mono<Result<List<Long>>> getMenuRoles(@PathVariable Long menuId) {
        log.info("请求查询菜单绑定的角色, menuId: {}", menuId);
        return menusService.getRoleIdsByMenu(menuId)
                .map(Result::success);
    }

    /**
     * 获取当前用户可见的菜单树
     *
     * <p>可见菜单完全由服务端按「登录用户 → 角色 → 菜单」推导，
     * <b>不接受客户端传入任何权限标识</b>。
     *
     * @return 菜单树
     */
    @GetMapping("/my")
    @Operation(summary = "获取当前用户的菜单", description = "按登录用户的角色推导可见菜单")
    public Mono<Result<List<Tree<Long>>>> getMyMenus() {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("请求获取当前用户菜单: userId={}", userId);
        return menusService.getUserMenusTree(userId)
                .map(Result::success);
    }

    /**
     * 获取用户当前权限下的菜单
     *
     * @deprecated 已被 {@code GET /menus/my} 取代。
     * 原实现直接采用<b>客户端请求体里的权限标识</b>去查菜单 ——
     * 等于让客户端自己证明自己有权限。本方法保留只是为了不打断旧前端，
     * <b>请求体会被完全忽略</b>。
     *
     * @return 菜单树
     */
    @Deprecated
    @PostMapping("/user")
    @Operation(summary = "获取用户菜单（已废弃）", description = "请改用 GET /menus/my")
    public Mono<Result<List<Tree<Long>>>> getUserMenus() {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("请求获取用户菜单（已废弃，改用 /menus/my）: userId={}", userId);
        return menusService.getUserMenusTree(userId)
                .map(Result::success);
    }

    /**
     * 创建菜单
     * 创建新的系统菜单，支持目录、菜单两种类型
     * （原「按钮」类型已随「菜单↔权限」模型下线）
     *
     * @param menusSaveDTO 菜单保存DTO对象
     * @return 操作结果
     */
    @SaCheckPermission(PermissionConstants.MENU_ADD)
    @PostMapping("/save")
    @Operation(summary = "创建菜单", description = "创建新的系统菜单")
    public Mono<Result<String>> saveMenus(@Valid @RequestBody MenusSaveDTO menusSaveDTO) {
        log.info("请求创建菜单: name={}", menusSaveDTO.getName());

        return menusService.saveMenu(OperatorContext.current(), menusSaveDTO)
                .thenReturn(Result.success("菜单创建成功"));
    }

    /**
     * 修改菜单
     * 更新现有菜单的信息
     *
     * @param menusUpdateDTO 菜单更新DTO对象
     * @return 操作结果
     */
    @SaCheckPermission(PermissionConstants.MENU_EDIT)
    @PutMapping("/edit")
    @Operation(summary = "修改菜单", description = "更新现有菜单")
    public Mono<Result<String>> updateMenus(@Valid @RequestBody MenusUpdateDTO menusUpdateDTO) {
        log.info("请求修改菜单: id={}", menusUpdateDTO.getId());

        return menusService.updateMenu(OperatorContext.current(), menusUpdateDTO)
                .thenReturn(Result.success("菜单更新成功"));
    }

    /**
     * 删除菜单
     * 删除指定ID的菜单及其关联关系
     *
     * @param id 菜单ID
     * @return 操作结果
     */
    @SaCheckPermission(PermissionConstants.MENU_REMOVE)
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除菜单", description = "删除指定ID的菜单")
    public Mono<Result<String>> delete(
            @PathVariable @Parameter(description = "菜单ID") Long id) {
        log.info("请求删除菜单: id={}", id);

        return menusService.deleteMenu(OperatorContext.current(), id)
                .thenReturn(Result.success("菜单删除成功"));
    }
}
