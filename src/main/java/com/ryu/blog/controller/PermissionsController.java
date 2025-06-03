package com.ryu.blog.controller;

import com.ryu.blog.dto.PermissionsAddDTO;
import com.ryu.blog.dto.PermissionsQueryDTO;
import com.ryu.blog.dto.PermissionsStatusDTO;
import com.ryu.blog.dto.PermissionsUpdateDTO;
import com.ryu.blog.entity.Permissions;
import com.ryu.blog.service.PermissionsService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
* 博客系统权限管理控制器
* 负责系统中权限的CRUD操作、权限分配和权限检查等功能
*
* @author ryu 475118582@qq.com
* @since 1.0.0 2024-08-10
*/
@RestController
@RequestMapping("/permissions")
@Tag(name="权限管理接口")
@AllArgsConstructor
@Slf4j
public class PermissionsController {
    private final PermissionsService permissionsService;

    /**
     * 获取所有权限列表
     * 获取系统中所有定义的权限，无分页
     *
     * @return 包含权限列表的结果对象
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有权限列表", description = "获取系统中所有的权限列表")
    public Mono<Result<List<Permissions>>> getPermissions() {
        log.info("请求获取所有权限列表");
        return permissionsService.list()
                .collectList()
                .map(permissionsList -> {
                    log.info("成功获取所有权限列表, 数量: {}", permissionsList.size());
                    return Result.success(permissionsList);
                });
    }

    /**
     * 修改权限
     * 修改现有权限的信息
     * 
     * @param permissionsUpdateDTO 权限更新DTO
     * @return 操作结果
     */
    @PutMapping("/edit")
    @Operation(summary = "修改权限", description = "修改现有权限")
    public Mono<Result<String>> updatePermissions(@RequestBody PermissionsUpdateDTO permissionsUpdateDTO) {
        log.info("请求修改权限: {}", permissionsUpdateDTO);
        
        // 参数校验
        if (permissionsUpdateDTO == null || permissionsUpdateDTO.getId() == null) {
            log.error("修改权限失败: 权限ID不能为空");
            return Mono.just(Result.error("权限ID不能为空"));
        }
        
        return permissionsService.updatePermission(permissionsUpdateDTO)
                .then(Mono.fromCallable(() -> {
                    log.info("成功修改权限, ID: {}", permissionsUpdateDTO.getId());
                    return Result.success("修改权限成功");
                }));
    }

    /**
     * 按模块获取权限列表
     * 获取指定模块下的所有权限
     * 
     * @param module 模块名称
     * @return 权限列表
     */
    @GetMapping("/module/{module}")
    @Operation(summary = "按模块获取权限列表", description = "获取指定模块下的所有权限")
    public Mono<Result<List<Permissions>>> getPermissionsByModule(@PathVariable String module) {
        log.info("请求按模块获取权限列表, module: {}", module);
        
        if (StringUtils.isBlank(module)) {
            log.warn("按模块获取权限失败: 模块名称为空");
            return Mono.just(Result.error("模块名称不能为空"));
        }
        
        return permissionsService.getPermissionsByModule(module)
                .collectList()
                .map(permissions -> {
                    log.info("成功按模块获取权限列表, module: {}, 数量: {}", module, permissions.size());
                    return Result.success(permissions);
                });
    }

    /**
     * 根据查询条件分页查询权限列表
     * 支持按名称、标识符和模块进行条件过滤
     *
     * @param permissionsQuery 查询条件DTO
     * @return 权限分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询权限列表", description = "根据查询条件分页获取权限列表")
    public Mono<Result<List<Permissions>>> getPermissionsByPage(@ParameterObject PermissionsQueryDTO permissionsQuery) {
        log.info("请求分页查询权限列表, 查询条件: {}", permissionsQuery);
        
        if (permissionsQuery == null) {
            log.warn("分页查询权限失败: 查询条件为空");
            return Mono.just(Result.error("查询条件不能为空"));
        }
        
        return permissionsService.getPermissionsByPage(permissionsQuery)
                .collectList()
                .map(permissionsList -> {
                    log.info("成功分页查询权限列表, 总数: {}", permissionsList.size());
                    return Result.success(permissionsList);
                });
    }

    /**
     * 新增权限
     * 添加新的系统权限定义
     *
     * @param permissionsAddDTO 权限信息DTO
     * @return 操作结果
     */
    @PostMapping("/add")
    @Operation(summary = "新增权限", description = "添加新的权限")
    public Mono<Result<String>> savePermission(@RequestBody PermissionsAddDTO permissionsAddDTO) {
        log.info("请求新增权限: {}", permissionsAddDTO);
        
        // 参数校验
        if (permissionsAddDTO == null) {
            log.error("新增权限失败: 参数为空");
            return Mono.just(Result.error("参数不能为空"));
        }
        
        if (StringUtils.isBlank(permissionsAddDTO.getName())) {
            log.error("新增权限失败: 权限名称为空");
            return Mono.just(Result.error("权限名称不能为空"));
        }
        
        if (StringUtils.isBlank(permissionsAddDTO.getIdentity())) {
            log.error("新增权限失败: 权限标识为空");
            return Mono.just(Result.error("权限标识不能为空"));
        }
        
        return permissionsService.savePermission(permissionsAddDTO)
                .then(Mono.fromCallable(() -> {
                    log.info("成功新增权限, 名称: {}, 标识: {}", permissionsAddDTO.getName(), permissionsAddDTO.getIdentity());
                    return Result.success("新增权限成功");
                }));
    }

    /**
     * 删除权限
     * 删除指定ID的权限，同时会清除相关关联关系
     *
     * @param id 权限ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除权限", description = "删除指定ID的权限")
    public Mono<Result<String>> delete(@PathVariable("id") Long id) {
        log.info("请求删除权限, ID: {}", id);
        
        if (id == null) {
            log.error("删除权限失败: 权限ID为空");
            return Mono.just(Result.error("权限ID不能为空"));
        }
        
        return permissionsService.removeById(id)
                .then(Mono.fromCallable(() -> {
                    log.info("成功删除权限, ID: {}", id);
                    return Result.success("删除权限成功");
                }));
    }
    
    /**
     * 获取用户权限标识列表
     * 获取指定用户拥有的所有权限标识，用于动态权限控制
     *
     * @param userId 用户ID
     * @return 权限标识集合
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户权限标识", description = "获取指定用户拥有的所有权限标识")
    public Mono<Result<Set<String>>> getUserPermissions(@PathVariable("userId") Long userId) {
        log.info("请求获取用户权限标识, userId: {}", userId);
        
        if (userId == null) {
            log.error("获取用户权限标识失败: 用户ID为空");
            return Mono.just(Result.error("用户ID不能为空"));
        }
        
        return permissionsService.getUserPermissionIdentities(userId)
                .map(permissions -> {
                    log.info("成功获取用户权限标识, userId: {}, 权限数量: {}", userId, permissions.size());
                    return Result.success(permissions);
                });
    }

    /**
     * 获取博客系统模块列表
     * 获取系统中定义的所有权限模块列表
     * 
     * @return 模块名称列表
     */
    @GetMapping("/modules")
    @Operation(summary = "获取博客系统模块列表", description = "获取系统中所有模块的列表")
    public Mono<Result<List<String>>> getModules() {
        log.info("请求获取博客系统模块列表");
        return permissionsService.getAllModules()
                .map(modules -> {
                    log.info("成功获取博客系统模块列表, 数量: {}", modules.size());
                    return Result.success(modules);
                });
    }

    /**
     * 检查用户是否拥有指定权限
     * 验证用户是否具有指定的权限标识
     *
     * @param userId 用户ID
     * @param permissionIdentity 权限标识
     * @return 是否拥有权限
     */
    @GetMapping("/check")
    @Operation(summary = "检查用户权限", description = "检查用户是否拥有指定权限")
    public Mono<Result<Boolean>> checkPermission(
            @Parameter(description = "用户ID") @RequestParam("userId") Long userId, 
            @Parameter(description = "权限标识") @RequestParam("identity") String permissionIdentity) {
        log.info("请求检查用户权限, userId: {}, permissionIdentity: {}", userId, permissionIdentity);
        
        if (userId == null) {
            log.error("检查用户权限失败: 用户ID为空");
            return Mono.just(Result.error("用户ID不能为空"));
        }
        
        return permissionsService.hasPermission(userId, permissionIdentity)
                .map(hasPermission -> {
                    log.info("成功检查用户权限, userId: {}, permissionIdentity: {}, 结果: {}", 
                            userId, permissionIdentity, hasPermission);
                    return Result.success(hasPermission);
                });
    }
    
    /**
     * 获取角色关联的权限列表
     * 获取指定角色拥有的所有权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    @GetMapping("/role/{roleId}")
    @Operation(summary = "获取角色权限列表", description = "获取指定角色拥有的所有权限")
    public Mono<Result<List<Permissions>>> getPermissionsByRole(@PathVariable("roleId") Long roleId) {
        log.info("请求获取角色权限列表, roleId: {}", roleId);
        
        if (roleId == null) {
            log.error("获取角色权限列表失败: 角色ID为空");
            return Mono.just(Result.error("角色ID不能为空"));
        }
        
        return permissionsService.getPermissionsByRoleId(roleId)
                .collectList()
                .map(permissions -> {
                    log.info("成功获取角色权限列表, roleId: {}, 权限数量: {}", roleId, permissions.size());
                    return Result.success(permissions);
                });
    }
    
    /**
     * 获取菜单关联的权限列表
     * 获取指定菜单关联的所有权限
     *
     * @param menuId 菜单ID
     * @return 权限列表
     */
    @GetMapping("/menu/{menuId}")
    @Operation(summary = "获取菜单权限列表", description = "获取指定菜单关联的所有权限")
    public Mono<Result<List<Permissions>>> getPermissionsByMenu(@PathVariable("menuId") Long menuId) {
        log.info("请求获取菜单权限列表, menuId: {}", menuId);
        
        if (menuId == null) {
            log.error("获取菜单权限列表失败: 菜单ID为空");
            return Mono.just(Result.error("菜单ID不能为空"));
        }
        
        return permissionsService.getPermissionsByMenuId(menuId)
                .collectList()
                .map(permissions -> {
                    log.info("成功获取菜单权限列表, menuId: {}, 权限数量: {}", menuId, permissions.size());
                    return Result.success(permissions);
                });
    }
    
    /**
     * 批量更新权限状态
     * 批量更新权限的激活状态
     *
     * @param statusDTO 包含权限ID列表和状态的DTO
     * @return 操作结果
     */
    @PutMapping("/status")
    @Operation(summary = "批量更新权限状态", description = "批量更新权限的状态")
    public Mono<Result<String>> updatePermissionStatus(@RequestBody PermissionsStatusDTO statusDTO) {
        log.info("请求批量更新权限状态: {}", statusDTO);
        
        // 参数校验
        if (statusDTO == null) {
            log.error("批量更新权限状态失败: 参数为空");
            return Mono.just(Result.error("参数不能为空"));
        }
        
        if (statusDTO.getIds() == null || statusDTO.getIds().isEmpty()) {
            log.error("批量更新权限状态失败: 权限ID列表为空");
            return Mono.just(Result.error("权限ID列表不能为空"));
        }
        
        if (statusDTO.getIsActive() == null) {
            log.error("批量更新权限状态失败: 激活状态为空");
            return Mono.just(Result.error("激活状态不能为空"));
        }
        
        return permissionsService.updatePermissionStatus(statusDTO.getIds(), statusDTO.getIsActive())
                .map(result -> {
                    if (result) {
                        log.info("成功批量更新权限状态, 权限数量: {}, 状态: {}", statusDTO.getIds().size(), statusDTO.getIsActive());
                        return Result.success("更新权限状态成功");
                    } else {
                        log.error("批量更新权限状态失败");
                        return Result.error("更新权限状态失败");
                    }
                });
    }
} 