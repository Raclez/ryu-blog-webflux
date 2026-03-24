package com.ryu.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ryu.blog.dto.PermissionsAssignDTO;
import com.ryu.blog.dto.RoleDTO;
import com.ryu.blog.dto.RoleListDTO;
import com.ryu.blog.dto.RoleUpdateDTO;
import com.ryu.blog.entity.Role;
import com.ryu.blog.entity.RolePermission;
import com.ryu.blog.entity.UserRole;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.repository.RolePermissionRepository;
import com.ryu.blog.repository.RoleRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.service.RoleService;
import com.ryu.blog.vo.PageResult;
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
import java.util.stream.Collectors;

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
        role.setIsActive(true);  // 默认激活
        role.setIsDefault(false); // 默认非默认角色
        role.setIsDeleted(false); // 未删除
        
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
        
        return roleRepository.findById(roleId)
                .filter(role -> !Integer.valueOf(1).equals(role.getIsDeleted()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在")))
                .flatMap(role -> 
                    rolePermissionRepository.deleteByRoleId(roleId)
                        .then(Mono.defer(() -> {
                            LocalDateTime now = LocalDateTime.now();
                            List<RolePermission> rolePermissions = permissionIds.stream()
                                    .map(permissionId -> {
                                        RolePermission rp = new RolePermission();
                                        rp.setRoleId(roleId);
                                        rp.setPermissionId(permissionId);
                                        rp.setCreateTime(now);
                                        rp.setUpdateTime(now);
                                        return rp;
                                    })
                                    .collect(Collectors.toList());
                            
                            return Flux.fromIterable(rolePermissions)
                                    .flatMap(rolePermissionRepository::save)
                                    .then();
                        }))
                );
    }

    @Override
    public Mono<PageResult<Role>> getRolesByConditions(RoleListDTO roleListDTO) {
        log.info("查询角色列表: {}", roleListDTO);
        
        // 获取分页参数
        long currentPage = roleListDTO.getCurrentPage() != null ? roleListDTO.getCurrentPage() : 1;
        long pageSize = roleListDTO.getPageSize() != null ? roleListDTO.getPageSize() : 10;
        int skip = (int) ((currentPage - 1) * pageSize);
        
        // 选择查询方法
        Flux<Role> roleFlux = (roleListDTO.getName() != null && !roleListDTO.getName().isEmpty())
                ? roleRepository.findByNameContainingAndIsDeleted(roleListDTO.getName(), 0)
                : roleRepository.findAllRoles();
        
        // 统一的分页处理
        return roleFlux
                .collectList()
                .map(allRoles -> buildPageResult(allRoles, currentPage, pageSize, skip));
    }
    
    /**
     * 构建分页结果
     */
    private PageResult<Role> buildPageResult(List<Role> allRoles, long currentPage, long pageSize, int skip) {
        long total = allRoles.size();
        List<Role> pageData = allRoles.stream()
                .skip(skip)
                .limit(pageSize)
                .collect(Collectors.toList());
        
        PageResult<Role> pageResult = new PageResult<>();
        pageResult.setRecords(pageData);
        pageResult.setTotal(total);
        pageResult.setSize(pageSize);
        pageResult.setCurrent(currentPage);
        pageResult.setPages((total + pageSize - 1) / pageSize);
        return pageResult;
    }

    @Override
    public Mono<RoleVO> getRoleDetails(Long id) {
        log.info("获取角色详情, id: {}", id);
        
        return roleRepository.findById(id)
                .filter(role -> !Integer.valueOf(1).equals(role.getIsDeleted()))
                .switchIfEmpty(Mono.empty())
                .flatMap(role -> {
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
                                        .doOnNext(roleVO::setPermissions)
                                        .thenReturn(roleVO);
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
        
        return roleRepository.findById(id)
                .filter(role -> !Integer.valueOf(1).equals(role.getIsDeleted()))
                .switchIfEmpty(Mono.empty())
                .flatMap(role -> {
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
                                        .doOnNext(rolePermissionsVO::setPermissions)
                                        .thenReturn(rolePermissionsVO);
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
    public Mono<Boolean> changeRoleStatus(Long roleId, Boolean isActive) {
        log.info("变更角色状态, roleId: {}, isActive: {}", roleId, isActive);

        return roleRepository.findById(roleId)
                .filter(role -> !Boolean.TRUE.equals(role.getIsDeleted()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在")))
                .flatMap(role -> {
                    role.setIsActive(isActive);
                    role.setUpdateTime(LocalDateTime.now());
                    return roleRepository.save(role).thenReturn(true);
                });
    }

    @Override
    @Transactional
    public Mono<Boolean> batchAssignUserRoles(List<Long> userIds, Long roleId, String assignBy) {
        log.info("批量分配用户角色, userIds: {}, roleId: {}, assignBy: {}", userIds, roleId, assignBy);
        
        return roleRepository.findById(roleId)
                .filter(role -> !Integer.valueOf(1).equals(role.getIsDeleted()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在")))
                .flatMap(role -> {
                    LocalDateTime now = LocalDateTime.now();
                    
                    List<UserRole> userRoles = userIds.stream()
                            .map(userId -> {
                                UserRole userRole = new UserRole();
                                userRole.setUserId(userId);
                                userRole.setRoleId(roleId);
                                userRole.setCreateTime(now);
                                userRole.setUpdateTime(now);
                                return userRole;
                            })
                            .collect(Collectors.toList());
                    
                    return Flux.fromIterable(userIds)
                            .flatMap(userId -> userRoleRepository.deleteByUserIdAndRoleId(userId, roleId))
                            .then(Flux.fromIterable(userRoles)
                                    .flatMap(userRoleRepository::save)
                                    .then(Mono.just(true)));
                });
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
        
        return roleRepository.findById(id)
                .filter(role -> !Integer.valueOf(1).equals(role.getIsDeleted()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在或已删除")))
                .flatMap(role -> {
                    // 逻辑删除
                    role.setIsDeleted(true);
                    role.setUpdateTime(LocalDateTime.now());
                    return roleRepository.save(role)
                            .then(rolePermissionRepository.deleteByRoleId(id))
                            .then(userRoleRepository.deleteByRoleId(id));
                });
    }

    @Override
    @Transactional
    public Mono<Role> updateRole(RoleUpdateDTO roleUpdateDTO) {
        log.info("更新角色信息, roleUpdateDTO: {}", roleUpdateDTO);
        
        return roleRepository.findById(roleUpdateDTO.getId())
                .filter(role -> !Integer.valueOf(1).equals(role.getIsDeleted()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("角色不存在或已删除")))
                .flatMap(role -> {
                    if (roleUpdateDTO.getName() != null) role.setName(roleUpdateDTO.getName());
                    if (roleUpdateDTO.getCode() != null) role.setCode(roleUpdateDTO.getCode());
                    if (roleUpdateDTO.getDescription() != null) role.setDescription(roleUpdateDTO.getDescription());
                    if (roleUpdateDTO.getSort() != null) role.setSort(roleUpdateDTO.getSort());
                    if (roleUpdateDTO.getIsActive() != null) {
                        role.setIsActive(roleUpdateDTO.getIsActive() == 1);
                    }
                    
                    role.setUpdateTime(LocalDateTime.now());
                    return roleRepository.save(role);
                });
    }
} 