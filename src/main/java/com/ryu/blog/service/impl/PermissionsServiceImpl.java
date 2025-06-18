package com.ryu.blog.service.impl;

import com.ryu.blog.dto.PermissionsAddDTO;
import com.ryu.blog.dto.PermissionsQueryDTO;
import com.ryu.blog.dto.PermissionsUpdateDTO;
import com.ryu.blog.entity.Permissions;
import com.ryu.blog.repository.MenuPermissionRepository;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.repository.RolePermissionRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.service.PermissionsService;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限服务实现类
 * 
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionsServiceImpl implements PermissionsService {

    private final PermissionsRepository permissionsRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MenuPermissionRepository menuPermissionRepository;
    private final UserRoleRepository userRoleRepository;
    
    @Override
    public Flux<Permissions> list() {
        log.info("获取所有权限");
        return permissionsRepository.findByIsDeletedOrderByIdAsc(0);
    }

    @Override
    @Transactional
    public Mono<Void> updatePermission(PermissionsUpdateDTO permissionsUpdateDTO) {
        log.info("更新权限: {}", permissionsUpdateDTO);
        
        // 查询权限是否存在
        return permissionsRepository.findById(permissionsUpdateDTO.getId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("权限不存在")))
                .flatMap(permission -> {
                    // 更新权限信息
                    permission.setName(permissionsUpdateDTO.getName());
                    permission.setIdentity(permissionsUpdateDTO.getIdentity());
                    permission.setDescription(permissionsUpdateDTO.getDescription());
                    if (permissionsUpdateDTO.getIsActive() != null) {
                        permission.setIsActive(permissionsUpdateDTO.getIsActive());
                    }
                    permission.setUpdateTime(LocalDateTime.now());
                    
                    // 保存更新
                    return permissionsRepository.save(permission);
                })
                .then();
    }

    @Override
    public Flux<Permissions> getPermissionsByModule(String modulePrefix) {
        log.info("获取模块权限, modulePrefix: {}", modulePrefix);
        return permissionsRepository.findByIdentityStartingWithAndIsDeletedOrderByIdAsc(modulePrefix + ":", 0);
    }

    @Override
    public Mono<PageResult<Permissions>> getPermissionsByPage(PermissionsQueryDTO permissionsQuery) {
        log.info("分页查询权限: {}", permissionsQuery);
        
        // 默认分页参数
        long currentPage = permissionsQuery.getCurrentPage() != null ? permissionsQuery.getCurrentPage() : 1;
        long pageSize = permissionsQuery.getPageSize() != null ? permissionsQuery.getPageSize() : 10;
        
        // 根据查询条件获取权限列表
        Flux<Permissions> permissionsFlux;
        
        if (permissionsQuery.getModulePrefix() != null && !permissionsQuery.getModulePrefix().isEmpty()) {
            permissionsFlux = permissionsRepository.findByIdentityStartingWithAndIsDeletedOrderByIdAsc(
                    permissionsQuery.getModulePrefix() + ":", 0);
        } else if (permissionsQuery.getName() != null && !permissionsQuery.getName().isEmpty()) {
            permissionsFlux = permissionsRepository.findByIsDeletedOrderByIdAsc(0)
                    .filter(permission -> permission.getName().contains(permissionsQuery.getName()));
        } else if (permissionsQuery.getIdentity() != null && !permissionsQuery.getIdentity().isEmpty()) {
            permissionsFlux = permissionsRepository.findByIsDeletedOrderByIdAsc(0)
                    .filter(permission -> permission.getIdentity().contains(permissionsQuery.getIdentity()));
        } else {
            permissionsFlux = permissionsRepository.findByIsDeletedOrderByIdAsc(0);
        }
        
        // 计算总数并构建分页结果
        return permissionsFlux.collectList()
                .map(allPermissions -> {
                    long total = allPermissions.size();
                    
                    // 计算分页
                    int fromIndex = (int) ((currentPage - 1) * pageSize);
                    if (fromIndex >= allPermissions.size()) {
                        fromIndex = 0;
                    }
                    
                    int toIndex = (int) Math.min(fromIndex + pageSize, allPermissions.size());
                    List<Permissions> pageData = allPermissions.subList(fromIndex, toIndex);
                    
                    // 创建分页结果对象
                    return new PageResult<>(pageData, total, pageSize, currentPage);
                });
    }

    @Override
    @Transactional
    public Mono<Void> savePermission(PermissionsAddDTO permissionsAddDTO) {
        log.info("保存权限: {}", permissionsAddDTO);
        
        // 创建权限实体
        Permissions permission = new Permissions();
        permission.setName(permissionsAddDTO.getName());
        permission.setIdentity(permissionsAddDTO.getIdentity());
        permission.setDescription(permissionsAddDTO.getDescription());
        permission.setIsActive(true); // 默认激活
        permission.setIsDeleted(false); // 未删除
        
        // 设置时间
        LocalDateTime now = LocalDateTime.now();
        permission.setCreateTime(now);
        permission.setUpdateTime(now);
        
        // 检查权限标识是否已存在
        return permissionsRepository.findByIdentity(permissionsAddDTO.getIdentity())
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("权限标识已存在"));
                    }
                    return permissionsRepository.save(permission).then();
                });
    }

    @Override
    @Transactional
    public Mono<Void> removeById(Long id) {
        log.info("删除权限, id: {}", id);
        
        // 查询权限是否存在
        return permissionsRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("权限不存在")))
                .flatMap(permission -> {
                    // 逻辑删除
                    permission.setIsDeleted(true);
                    permission.setUpdateTime(LocalDateTime.now());
                    
                    return permissionsRepository.save(permission)
                            // 删除角色权限关联
                            .thenMany(rolePermissionRepository.findRoleIdsByPermissionId(id))
                            .flatMap(roleId -> rolePermissionRepository.deleteByRoleIdAndPermissionIdIn(roleId, List.of(id)))
                            // 删除菜单权限关联
                            .thenMany(menuPermissionRepository.findMenuIdsByPermissionIds(List.of(id)))
                            .flatMap(menuId -> menuPermissionRepository.deleteByMenuId(menuId))
                            .then();
                });
    }

    @Override
    public Mono<Set<String>> getUserPermissionIdentities(Long userId) {
        log.info("获取用户权限标识, userId: {}", userId);
        
        // 获取用户角色ID
        return userRoleRepository.findRoleIdsByUserId(userId)
                .collectList()
                .flatMap(roleIds -> {
                    if (roleIds.isEmpty()) {
                        return Mono.just(new HashSet<>());
                    }
                    
                    // 获取角色权限ID
                    return Flux.fromIterable(roleIds)
                            .flatMap(rolePermissionRepository::findPermissionIdsByRoleId)
                            .collectList()
                            .flatMap(permissionIds -> {
                                if (permissionIds.isEmpty()) {
                                    return Mono.just(new HashSet<>());
                                }
                                
                                // 获取权限标识
                                return permissionsRepository.findByIdIn(permissionIds)
                                        .filter(permission -> permission.getIsActive() == true && permission.getIsDeleted() == false)
                                        .map(Permissions::getIdentity)
                                        .collect(Collectors.toSet());
                            });
                });
    }

    @Override
    public Mono<List<String>> getAllModules() {
        log.info("获取所有模块");
        
        return permissionsRepository.findByIsDeletedOrderByIdAsc(0)
                .map(permission -> {
                    String identity = permission.getIdentity();
                    int index = identity.indexOf(":");
                    return index > 0 ? identity.substring(0, index) : "common";
                })
                .distinct()
                .collectList();
    }

    @Override
    public Mono<Boolean> hasPermission(Long userId, String permissionIdentity) {
        log.info("检查用户权限, userId: {}, permissionIdentity: {}", userId, permissionIdentity);
        
        // 先查询权限ID
        return permissionsRepository.findByIdentity(permissionIdentity)
                .switchIfEmpty(Mono.just(new Permissions())) // 权限不存在，返回空对象
                .flatMap(permission -> {
                    if (permission.getId() == null || permission.getIsActive() != true || permission.getIsDeleted() == true) {
                        return Mono.just(false);
                    }
                    
                    // 查询拥有该权限的角色ID
                    return rolePermissionRepository.findRoleIdsByPermissionId(permission.getId())
                            .collectList()
                            .flatMap(roleIds -> {
                                if (roleIds.isEmpty()) {
                                    return Mono.just(false);
                                }
                                
                                // 查询用户是否拥有这些角色中的任意一个
                                return userRoleRepository.findRoleIdsByUserId(userId)
                                        .collectList()
                                        .map(userRoleIds -> {
                                            for (Long roleId : roleIds) {
                                                if (userRoleIds.contains(roleId)) {
                                                    return true;
                                                }
                                            }
                                            return false;
                                        });
                            });
                });
    }

    @Override
    public Flux<Permissions> getPermissionsByRoleId(Long roleId) {
        log.info("获取角色权限, roleId: {}", roleId);
        
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId)
                .collectList()
                .flatMapMany(permissionIds -> {
                    if (permissionIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return permissionsRepository.findByIdIn(permissionIds);
                });
    }

    @Override
    public Flux<Permissions> getPermissionsByMenuId(Long menuId) {
        log.info("获取菜单权限, menuId: {}", menuId);
        
        return menuPermissionRepository.findPermissionIdsByMenuId(menuId)
                .collectList()
                .flatMapMany(permissionIds -> {
                    if (permissionIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return permissionsRepository.findByIdIn(permissionIds);
                });
    }

    @Override
    @Transactional
    public Mono<Boolean> updatePermissionStatus(List<Long> ids, Boolean isActive) {
        log.info("批量更新权限状态, ids: {}, isActive: {}", ids, isActive);
        
        if (ids == null || ids.isEmpty()) {
            return Mono.just(false);
        }
        
        return permissionsRepository.findByIdIn(ids)
                .flatMap(permission -> {
                    permission.setIsActive(isActive);
                    permission.setUpdateTime(LocalDateTime.now());
                    return permissionsRepository.save(permission);
                })
                .then(Mono.just(true))
                .defaultIfEmpty(false);
    }
} 