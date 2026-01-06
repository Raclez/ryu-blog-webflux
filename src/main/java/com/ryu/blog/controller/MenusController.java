package com.ryu.blog.controller;

import cn.hutool.core.lang.tree.Tree;
import com.ryu.blog.dto.MenusSaveDTO;
import com.ryu.blog.dto.MenusUpdateDTO;
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
* 负责菜单的CRUD操作、菜单权限绑定和用户菜单获取等功能
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
    @GetMapping("/tree")
    @Operation(summary = "获取菜单树形结构", description = "获取系统菜单的树形结构")
    public Mono<Result<List<Tree<Long>>>> tree() {
        log.info("请求获取菜单树形结构");
        return menusService.getMenusByTree()
                .map(menusByTree -> {
                    log.info("成功获取菜单树形结构, 记录数：{}", menusByTree.size());
                    return Result.success(menusByTree);
                });
    }

    /**
     * 获取所有菜单
     * 返回系统中所有菜单的平铺列表，不含层级关系
     *
     * @return 包含所有菜单的结果对象
     */
    @GetMapping("/all")
    @Operation(summary = "获取所有菜单", description = "获取系统中所有菜单的完整列表")
    public Mono<Result<List<Menus>>> getAllMenus() {
        log.info("请求获取所有菜单");
        return menusService.listAll()
                .map(menusEntities -> {
                    log.info("成功获取所有菜单, 总数: {}", menusEntities.size());
                    return Result.success(menusEntities);
                });
    }

    /**
     * 绑定权限到菜单
     * 将一个或多个权限绑定到指定菜单，用于实现基于菜单的权限控制
     *
     * @param permissionIds 权限ID列表
     * @param menuId        菜单ID
     * @return 操作结果
     */
    @PostMapping("/bindPermissions")
    @Operation(summary = "绑定权限到菜单", description = "将权限绑定到指定的菜单")
    public Mono<Result<String>> bindPermissions(
            @RequestBody @Parameter(description = "权限ID列表") List<Long> permissionIds, 
            @RequestParam @Parameter(description = "菜单ID") Long menuId) {
        log.info("请求绑定权限到菜单, menuId: {}, permissionIds: {}", menuId, permissionIds);
        
        return menusService.bindPermissions(permissionIds, menuId)
                .thenReturn(Result.success("绑定权限成功"))
                .doOnSuccess(result -> log.info("成功绑定权限到菜单, menuId: {}, 权限数量: {}", 
                        menuId, permissionIds != null ? permissionIds.size() : 0));
    }

    /**
     * 获取用户当前权限下的菜单
     * 根据用户权限标识列表，获取用户有权访问的菜单
     *
     * @param permissionIdentities 用户权限标识列表
     * @return 用户可访问的菜单列表
     */
    @PostMapping("/user")
    @Operation(summary = "获取用户菜单", description = "获取用户有权限访问的菜单")
    public Mono<Result<List<Menus>>> getUserMenus(
            @RequestBody @Parameter(description = "用户权限标识列表") List<String> permissionIdentities) {
        log.info("请求获取用户菜单, 权限标识列表: {}", permissionIdentities);
        
        return menusService.getUserMenus(permissionIdentities)
                .collectList()
                .map(menusEntities -> {
                    log.info("成功获取用户菜单, 菜单数量: {}", menusEntities.size());
                    return Result.success(menusEntities);
                });
    }

    /**
     * 创建菜单
     * 创建新的系统菜单，支持目录、菜单和按钮三种类型
     *
     * @param menusSaveDTO 菜单保存DTO对象
     * @return 操作结果
     */
    @PostMapping("/save")
    @Operation(summary = "创建菜单", description = "创建新的菜单")
    public Mono<Result<String>> saveMenus(@Valid @RequestBody MenusSaveDTO menusSaveDTO) {
        log.info("请求创建菜单: {}", menusSaveDTO);
        
        return menusService.saveMenu(menusSaveDTO)
                .thenReturn(Result.success("菜单创建成功"))
                .doOnSuccess(result -> log.info("成功创建菜单: {}", menusSaveDTO.getName()));
    }

    /**
     * 修改菜单
     * 修改现有菜单的信息
     *
     * @param menusUpdateDTO 菜单更新DTO对象
     * @return 操作结果
     */
    @PutMapping("/edit")
    @Operation(summary = "修改菜单", description = "修改现有菜单")
    public Mono<Result<String>> updateMenus(@Valid @RequestBody MenusUpdateDTO menusUpdateDTO) {
        log.info("请求修改菜单: {}", menusUpdateDTO);
        
        return menusService.updateMenu(menusUpdateDTO)
                .thenReturn(Result.success("菜单更新成功"))
                .doOnSuccess(result -> log.info("成功修改菜单, ID: {}, 名称: {}", 
                        menusUpdateDTO.getId(), menusUpdateDTO.getName()));
    }

    /**
     * 删除菜单
     * 删除指定ID的菜单及其关联关系
     *
     * @param id 菜单ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除菜单", description = "删除指定ID的菜单")
    public Mono<Result<String>> delete(
            @PathVariable @Parameter(description = "菜单ID") Long id) {
        log.info("请求删除菜单, ID: {}", id);
        
        return menusService.deleteMenu(id)
                .thenReturn(Result.success("菜单删除成功"))
                .doOnSuccess(result -> log.info("成功删除菜单, ID: {}", id));
    }
} 