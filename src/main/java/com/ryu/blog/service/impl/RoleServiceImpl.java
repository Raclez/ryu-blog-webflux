package com.ryu.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ryu.blog.dto.PermissionsAssignDTO;
import com.ryu.blog.dto.RoleDTO;
import com.ryu.blog.dto.RoleListDTO;
import com.ryu.blog.entity.Permissions;
import com.ryu.blog.entity.Role;
import com.ryu.blog.entity.RolePermission;
import com.ryu.blog.entity.UserRole;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.repository.RolePermissionRepository;
import com.ryu.blog.repository.RoleRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.service.RoleService;
import com.ryu.blog.vo.RolePermissionsVO;
import com.ryu.blog.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色服务实现类
 * 
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionsRepository permissionsRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional
    public Mono<Void> saveRole(RoleDTO roleDTO) {
        log.info("保存角色: {}", roleDTO);
        
        // 创建角色
        Role role = new Role();
        role.setName(roleDTO.getName());
        role.setCode(roleDTO.getCode());
        role.setDescription(roleDTO.getDescription());
        role.setSort(roleDTO.getSort() != null ? roleDTO.getSort() : 0);
        role.setIsActive(1);  // 默认激活
        role.setIsDefault(0); // 默认非默认角色
        role.setIsDeleted(0); // 未删除
        
        // 设置时间
        LocalDateTime now = LocalDateTime.now();
        role.setCreateTime(now);
        role.setUpdateTime(now);
        
        // 保存角色
        return roleRepository.save(role).then();
    }

    @Override
    @Transactional
    public Mono<Void> assignPermissions(PermissionsAssignDTO permissionsAssignDTO) {
        log.info("为角色分配权限: {}", permissionsAssignDTO);
        
        Long roleId = permissionsAssignDTO.getRoleId();
        List<Long> permissionIds = permissionsAssignDTO.getPermissionIds();
        
        // 先查询角色是否存在
        return roleRepository.findById(roleId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在")))
                .flatMap(role -> {
                    // 删除原有权限
                    return rolePermissionRepository.deleteByRoleId(roleId)
                            .then(Mono.defer(() -> {
                                // 保存新权限
                                List<RolePermission> rolePermissions = new ArrayList<>();
                                LocalDateTime now = LocalDateTime.now();
                                
                                for (Long permissionId : permissionIds) {
                                    RolePermission rolePermission = new RolePermission();
                                    rolePermission.setRoleId(roleId);
                                    rolePermission.setPermissionId(permissionId);
                                    rolePermission.setCreateTime(now);
                                    rolePermission.setUpdateTime(now);
                                    rolePermissions.add(rolePermission);
                                }
                                
                                return Flux.fromIterable(rolePermissions)
                                        .flatMap(rolePermissionRepository::save)
                                        .then();
                            }));
                });
    }

    @Override
    public Flux<Role> getRoles(RoleListDTO roleListDTO) {
        log.info("查询角色列表: {}", roleListDTO);
        
        if (roleListDTO.getName() != null && !roleListDTO.getName().isEmpty()) {
            // 按名称模糊查询
            return roleRepository.findByNameContainingAndIsDeleted(roleListDTO.getName(), 0);
        } else {
            // 查询所有未删除的角色
            return roleRepository.findAllRoles();
        }
    }

    @Override
    public Mono<RoleVO> getRoleDetails(Long id) {
        log.info("获取角色详情, id: {}", id);
        
        // 查询角色
        return roleRepository.findById(id)
                .filter(role -> role.getIsDeleted() == 0)
                .switchIfEmpty(Mono.empty())
                .flatMap(role -> {
                    // 转换为VO
                    RoleVO roleVO = new RoleVO();
                    BeanUtil.copyProperties(role, roleVO);
                    
                    // 查询角色权限
                    return rolePermissionRepository.findPermissionIdsByRoleId(id)
                            .collectList()
                            .flatMap(permissionIds -> {
                                if (permissionIds.isEmpty()) {
                                    roleVO.setPermissions(new ArrayList<>());
                                    return Mono.just(roleVO);
                                }
                                
                                return permissionsRepository.findByIdIn(permissionIds)
                                        .collectList()
                                        .map(permissions -> {
                                            roleVO.setPermissions(permissions);
                                            return roleVO;
                                        });
                            });
                });
    }

    @Override
    @Transactional
    public Mono<Void> removePermission(PermissionsAssignDTO permissionsAssignDTO) {
        log.info("删除角色权限: {}", permissionsAssignDTO);
        
        Long roleId = permissionsAssignDTO.getRoleId();
        List<Long> permissionIds = permissionsAssignDTO.getPermissionIds();
        
        // 删除指定权限
        return rolePermissionRepository.deleteByRoleIdAndPermissionIdIn(roleId, permissionIds);
    }

    @Override
    public Mono<RolePermissionsVO> getRolePermissions(Long id) {
        log.info("获取角色权限, id: {}", id);
        
        // 查询角色
        return roleRepository.findById(id)
                .filter(role -> role.getIsDeleted() == 0)
                .switchIfEmpty(Mono.empty())
                .flatMap(role -> {
                    // 创建返回对象
                    RolePermissionsVO rolePermissionsVO = new RolePermissionsVO();
                    rolePermissionsVO.setRole(role);
                    
                    // 查询角色权限
                    return rolePermissionRepository.findPermissionIdsByRoleId(id)
                            .collectList()
                            .flatMap(permissionIds -> {
                                if (permissionIds.isEmpty()) {
                                    rolePermissionsVO.setPermissions(new ArrayList<>());
                                    return Mono.just(rolePermissionsVO);
                                }
                                
                                return permissionsRepository.findByIdIn(permissionIds)
                                        .collectList()
                                        .map(permissions -> {
                                            rolePermissionsVO.setPermissions(permissions);
                                            return rolePermissionsVO;
                                        });
                            });
                });
    }

    @Override
    public Mono<Role> getDefaultRole() {
        log.info("获取默认角色");
        
        return roleRepository.findByIsDefaultAndIsDeleted(1, 0)
                .switchIfEmpty(Mono.empty());
    }

    @Override
    public Flux<Role> getUserRoles(Long userId) {
        log.info("获取用户角色, userId: {}", userId);
        
        return roleRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public Mono<Boolean> changeRoleStatus(Long roleId, Integer isActive) {
        log.info("变更角色状态, roleId: {}, isActive: {}", roleId, isActive);
        
        return roleRepository.findById(roleId)
                .filter(role -> role.getIsDeleted() == 0)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在")))
                .flatMap(role -> {
                    role.setIsActive(isActive);
                    role.setUpdateTime(LocalDateTime.now());
                    return roleRepository.save(role)
                            .map(savedRole -> true);
                })
                .defaultIfEmpty(false);
    }

    @Override
    @Transactional
    public Mono<Boolean> batchAssignUserRoles(List<Long> userIds, Long roleId, String assignBy) {
        log.info("批量分配用户角色, userIds: {}, roleId: {}, assignBy: {}", userIds, roleId, assignBy);
        
        // 检查角色是否存在
        return roleRepository.findById(roleId)
                .filter(role -> role.getIsDeleted() == 0)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在")))
                .flatMap(role -> {
                    // 为每个用户创建角色关联
                    List<UserRole> userRoles = new ArrayList<>();
                    LocalDateTime now = LocalDateTime.now();
                    
                    for (Long userId : userIds) {
                        UserRole userRole = new UserRole();
                        userRole.setUserId(userId);
                        userRole.setRoleId(roleId);
                        userRole.setCreateTime(now);
                        userRole.setUpdateTime(now);
                        userRoles.add(userRole);
                    }
                    
                    // 先删除已有的角色关联
                    return Flux.fromIterable(userIds)
                            .flatMap(userId -> userRoleRepository.deleteByUserIdAndRoleId(userId, roleId))
                            .then(Flux.fromIterable(userRoles)
                                    .flatMap(userRoleRepository::save)
                                    .then(Mono.just(true)));
                })
                .defaultIfEmpty(false);
    }

    @Override
    public Flux<Role> getAllRoles() {
        log.info("获取所有角色");
        
        return roleRepository.findAllRoles();
    }

    @Override
    @Transactional
    public Mono<Void> removeById(Long id) {
        log.info("删除角色, id: {}", id);
        
        // 查询角色
        return roleRepository.findById(id)
                .filter(role -> role.getIsDeleted() == 0)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在")))
                .flatMap(role -> {
                    // 逻辑删除
                    role.setIsDeleted(1);
                    role.setUpdateTime(LocalDateTime.now());
                    return roleRepository.save(role)
                            // 删除角色权限关联
                            .then(rolePermissionRepository.deleteByRoleId(id))
                            // 删除用户角色关联
                            .then(userRoleRepository.deleteByRoleId(id));
                });
    }
} 