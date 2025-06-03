package com.ryu.blog.controller;

import com.ryu.blog.dto.BatchAssignRoleDTO;
import com.ryu.blog.dto.PermissionsAssignDTO;
import com.ryu.blog.dto.RoleDTO;
import com.ryu.blog.dto.RoleListDTO;
import com.ryu.blog.dto.RoleStatusDTO;
import com.ryu.blog.entity.Role;
import com.ryu.blog.service.RoleService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.RolePermissionsVO;
import com.ryu.blog.vo.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
* 角色管理控制器
* 负责系统中用户角色的CRUD操作、角色权限分配和用户角色关系管理等功能
*
* @author ryu 475118582@qq.com
* @since 1.0.0 2024-08-10
*/
@RestController
@RequestMapping("/roles")
@Tag(name="角色管理接口")
@AllArgsConstructor
@Slf4j
public class RoleController {
    private final RoleService roleService;

    /**
     * 保存角色
     * 添加新的系统角色
     *
     * @param roleDTO 角色信息DTO
     * @return 操作结果
     */
    @PostMapping("/add")
    @Operation(summary = "保存角色", description = "添加新的角色")
    public Mono<Result<String>> saveRole(@RequestBody RoleDTO roleDTO) {
        log.info("请求保存角色: {}", roleDTO);
        
        // 参数校验
        if (roleDTO == null) {
            log.error("保存角色失败: 参数为空");
            return Mono.just(Result.error("参数不能为空"));
        }
        
        if (StringUtils.isBlank(roleDTO.getName())) {
            log.error("保存角色失败: 角色名称为空");
            return Mono.just(Result.error("角色名称不能为空"));
        }
        
        if (StringUtils.isBlank(roleDTO.getCode())) {
            log.error("保存角色失败: 角色编码为空");
            return Mono.just(Result.error("角色编码不能为空"));
        }
        
        return roleService.saveRole(roleDTO)
                .then(Mono.fromCallable(() -> {
                    log.info("成功保存角色: {}", roleDTO.getName());
                    return Result.success("角色保存成功");
                }));
    }

    /**
     * 删除角色
     * 删除指定ID的角色，同时会清除相关角色权限关联和用户角色关联
     *
     * @param id 角色ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除角色", description = "删除指定ID的角色")
    public Mono<Result<String>> deleteRole(@PathVariable("id") @Parameter(description = "角色ID") Long id) {
        log.info("请求删除角色, ID: {}", id);
        
        if (id == null) {
            log.error("删除角色失败: 角色ID为空");
            return Mono.just(Result.error("角色ID不能为空"));
        }
        
        return roleService.removeById(id)
                .then(Mono.fromCallable(() -> {
                    log.info("成功删除角色, ID: {}", id);
                    return Result.success("角色删除成功");
                }))
                .onErrorResume(e -> {
                    log.error("删除角色失败, ID: {}, 错误: {}", id, e.getMessage());
                    return Mono.just(Result.error("角色删除失败: " + e.getMessage()));
                });
    }

    /**
     * 分配角色权限
     * 为指定角色分配一组权限
     *
     * @param permissionsAssignDTO 权限分配DTO
     * @return 操作结果
     */
    @PostMapping("/assign-permissions")
    @Operation(summary = "分配角色权限", description = "为角色分配权限")
    public Mono<Result<String>> assignPermissions(@RequestBody PermissionsAssignDTO permissionsAssignDTO) {
        log.info("请求为角色分配权限: {}", permissionsAssignDTO);
        
        // 参数校验
        if (permissionsAssignDTO == null) {
            log.error("分配角色权限失败: 参数为空");
            return Mono.just(Result.error("参数不能为空"));
        }
        
        if (permissionsAssignDTO.getRoleId() == null) {
            log.error("分配角色权限失败: 角色ID为空");
            return Mono.just(Result.error("角色ID不能为空"));
        }
        
        if (permissionsAssignDTO.getPermissionIds() == null || permissionsAssignDTO.getPermissionIds().isEmpty()) {
            log.error("分配角色权限失败: 权限ID列表为空");
            return Mono.just(Result.error("权限ID列表不能为空"));
        }
        
        return roleService.assignPermissions(permissionsAssignDTO)
                .then(Mono.fromCallable(() -> {
                    log.info("成功为角色分配权限, roleId: {}, 权限数量: {}", 
                            permissionsAssignDTO.getRoleId(), permissionsAssignDTO.getPermissionIds().size());
                    return Result.success("角色权限分配成功");
                }));
    }

    /**
     * 获取角色列表
     * 分页获取角色列表，支持按名称过滤
     *
     * @param roleListDTO 角色查询条件DTO
     * @return 角色分页列表
     */
    @GetMapping("/page")
    @Operation(summary = "获取角色列表", description = "获取角色列表，支持分页和条件查询")
    public Mono<Result<List<Role>>> getRoles(@ParameterObject RoleListDTO roleListDTO) {
        log.info("请求获取角色列表: {}", roleListDTO);
        
        if (roleListDTO == null) {
            log.warn("获取角色列表失败: 查询条件为空");
            return Mono.just(Result.error("查询条件不能为空"));
        }
        
        return roleService.getRoles(roleListDTO)
                .collectList()
                .map(roles -> {
                    log.info("成功获取角色列表, 总数: {}", roles.size());
                    return Result.success(roles);
                });
    }

    /**
     * 查询角色详情
     * 获取指定角色的详细信息，包含关联的权限
     *
     * @param id 角色ID
     * @return 角色详情，包含权限信息
     */
    @GetMapping("/get/{id}")
    @Operation(summary = "获取角色详情", description = "获取指定角色的详细信息（包含权限）")
    public Mono<Result<RoleVO>> getRoleDetails(@PathVariable("id") @Parameter(description = "角色ID") Long id) {
        log.info("请求获取角色详情, ID: {}", id);
        
        if (id == null) {
            log.error("获取角色详情失败: 角色ID为空");
            return Mono.just(Result.error("角色ID不能为空"));
        }
        
        return roleService.getRoleDetails(id)
                .map(roleVO -> {
                    log.info("成功获取角色详情, ID: {}, 名称: {}", id, roleVO.getName());
                    return Result.success(roleVO);
                })
                .defaultIfEmpty(Result.error("角色不存在"));
    }

    /**
     * 删除角色权限
     * 移除角色的指定权限
     * 
     * @param permissionsAssignDTO 角色权限DTO
     * @return 操作结果
     */
    @PostMapping("/removePermission")
    @Operation(summary = "删除角色权限", description = "删除角色的权限")
    public Mono<Result<String>> removePermission(@RequestBody PermissionsAssignDTO permissionsAssignDTO) {
        log.info("请求删除角色权限: {}", permissionsAssignDTO);
        
        // 参数校验
        if (permissionsAssignDTO == null) {
            log.error("删除角色权限失败: 参数为空");
            return Mono.just(Result.error("参数不能为空"));
        }
        
        if (permissionsAssignDTO.getRoleId() == null) {
            log.error("删除角色权限失败: 角色ID为空");
            return Mono.just(Result.error("角色ID不能为空"));
        }
        
        if (permissionsAssignDTO.getPermissionIds() == null || permissionsAssignDTO.getPermissionIds().isEmpty()) {
            log.error("删除角色权限失败: 权限ID列表为空");
            return Mono.just(Result.error("权限ID列表不能为空"));
        }
        
        return roleService.removePermission(permissionsAssignDTO)
                .then(Mono.fromCallable(() -> {
                    log.info("成功删除角色权限, roleId: {}, 权限数量: {}", 
                            permissionsAssignDTO.getRoleId(), permissionsAssignDTO.getPermissionIds().size());
                    return Result.success("角色权限删除成功");
                }));
    }

    /**
     * 获取角色的所有权限
     * 获取指定角色拥有的所有权限信息
     * 
     * @param id 角色ID
     * @return 角色权限信息
     */
    @GetMapping("/{id}/permissions")
    @Operation(summary = "获取角色权限", description = "获取指定角色的所有权限")
    public Mono<Result<RolePermissionsVO>> getRolePermissions(@PathVariable("id") @Parameter(description = "角色ID") Long id) {
        log.info("请求获取角色的所有权限, ID: {}", id);
        
        if (id == null) {
            log.error("获取角色权限失败: 角色ID为空");
            return Mono.just(Result.error("角色ID不能为空"));
        }
        
        return roleService.getRolePermissions(id)
                .map(permissions -> {
                    log.info("成功获取角色权限, ID: {}, 权限数量: {}", 
                            id, permissions.getPermissions() != null ? permissions.getPermissions().size() : 0);
                    return Result.success(permissions);
                })
                .defaultIfEmpty(Result.error("角色不存在"));
    }
    
    /**
     * 获取默认角色
     * 获取系统设置的默认角色
     * 
     * @return 默认角色信息
     */
    @GetMapping("/default")
    @Operation(summary = "获取默认角色", description = "获取系统默认角色")
    public Mono<Result<Role>> getDefaultRole() {
        log.info("请求获取默认角色");
        return roleService.getDefaultRole()
                .map(defaultRole -> {
                    log.info("成功获取默认角色, ID: {}, 名称: {}", defaultRole.getId(), defaultRole.getName());
                    return Result.success(defaultRole);
                })
                .defaultIfEmpty(Result.error("系统未设置默认角色"));
    }
    
    /**
     * 获取用户的所有角色
     * 获取指定用户拥有的所有角色
     * 
     * @param userId 用户ID
     * @return 角色列表
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户角色", description = "获取用户拥有的所有角色")
    public Mono<Result<List<Role>>> getUserRoles(
            @PathVariable("userId") @Parameter(description = "用户ID") Long userId) {
        log.info("请求获取用户角色, userId: {}", userId);
        
        if (userId == null) {
            log.error("获取用户角色失败: 用户ID为空");
            return Mono.just(Result.error("用户ID不能为空"));
        }
        
        return roleService.getUserRoles(userId)
                .collectList()
                .map(roles -> {
                    log.info("成功获取用户角色, userId: {}, 角色数量: {}", userId, roles.size());
                    return Result.success(roles);
                });
    }
    
    /**
     * 更改角色状态
     * 启用或禁用指定角色
     * 
     * @param roleStatusDTO 角色状态DTO
     * @return 操作结果
     */
    @PostMapping("/changeStatus")
    @Operation(summary = "更改角色状态", description = "启用或禁用角色")
    public Mono<Result<String>> changeRoleStatus(@RequestBody RoleStatusDTO roleStatusDTO) {
        log.info("请求更改角色状态: {}", roleStatusDTO);
        
        if (roleStatusDTO == null) {
            log.error("更改角色状态失败: 参数为空");
            return Mono.just(Result.error("参数不能为空"));
        }
        
        if (roleStatusDTO.getRoleId() == null) {
            log.error("更改角色状态失败: 角色ID为空");
            return Mono.just(Result.error("角色ID不能为空"));
        }
        
        if (roleStatusDTO.getIsActive() == null) {
            log.error("更改角色状态失败: 状态为空");
            return Mono.just(Result.error("状态不能为空"));
        }
        
        return roleService.changeRoleStatus(roleStatusDTO.getRoleId(), roleStatusDTO.getIsActive())
                .map(success -> {
                    if (success) {
                        log.info("成功更改角色状态, roleId: {}, isActive: {}", 
                                roleStatusDTO.getRoleId(), roleStatusDTO.getIsActive());
                        return Result.success("角色状态更改成功");
                    } else {
                        log.error("更改角色状态失败, roleId: {}", roleStatusDTO.getRoleId());
                        return Result.error("角色状态更改失败");
                    }
                });
    }
    
    /**
     * 批量分配用户角色
     * 为多个用户分配同一个角色
     * 
     * @param batchAssignRoleDTO 批量分配角色DTO
     * @return 操作结果
     */
    @PostMapping("/batchAssign")
    @Operation(summary = "批量分配用户角色", description = "为多个用户分配同一个角色")
    public Mono<Result<String>> batchAssignUserRoles(@RequestBody BatchAssignRoleDTO batchAssignRoleDTO) {
        log.info("请求批量分配用户角色: {}", batchAssignRoleDTO);
        
        if (batchAssignRoleDTO == null) {
            log.error("批量分配用户角色失败: 参数为空");
            return Mono.just(Result.error("参数不能为空"));
        }
        
        if (batchAssignRoleDTO.getRoleId() == null) {
            log.error("批量分配用户角色失败: 角色ID为空");
            return Mono.just(Result.error("角色ID不能为空"));
        }
        
        if (batchAssignRoleDTO.getUserIds() == null || batchAssignRoleDTO.getUserIds().isEmpty()) {
            log.error("批量分配用户角色失败: 用户ID列表为空");
            return Mono.just(Result.error("用户ID列表不能为空"));
        }
        
        return roleService.batchAssignUserRoles(
                    batchAssignRoleDTO.getUserIds(), 
                    batchAssignRoleDTO.getRoleId(), 
                    batchAssignRoleDTO.getAssignBy()
                )
                .map(success -> {
                    if (success) {
                        log.info("成功批量分配用户角色, roleId: {}, 用户数量: {}", 
                                batchAssignRoleDTO.getRoleId(), batchAssignRoleDTO.getUserIds().size());
                        return Result.success("批量分配用户角色成功");
                    } else {
                        log.error("批量分配用户角色失败, roleId: {}", batchAssignRoleDTO.getRoleId());
                        return Result.error("批量分配用户角色失败");
                    }
                });
    }
    
    /**
     * 获取所有角色
     * 获取系统中所有的角色列表
     * 
     * @return 角色列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有角色", description = "获取系统中所有角色")
    public Mono<Result<List<Role>>> getAllRoles() {
        log.info("请求获取所有角色");
        return roleService.getAllRoles()
                .collectList()
                .map(roles -> {
                    log.info("成功获取所有角色, 总数: {}", roles.size());
                    return Result.success(roles);
                });
    }
} 