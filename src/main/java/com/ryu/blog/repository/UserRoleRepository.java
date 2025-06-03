package com.ryu.blog.repository;

import com.ryu.blog.entity.UserRole;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 用户角色关联仓库接口
 * 提供对用户角色关联数据的访问操作
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Repository
public interface UserRoleRepository extends R2dbcRepository<UserRole, Long> {
    
    /**
     * 根据用户ID查询关联的角色ID列表
     * 
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @Query("SELECT role_id FROM t_user_roles WHERE user_id = :userId")
    Flux<Long> findRoleIdsByUserId(Long userId);
    
    /**
     * 根据角色ID查询关联的用户ID列表
     * 
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    @Query("SELECT user_id FROM t_user_roles WHERE role_id = :roleId")
    Flux<Long> findUserIdsByRoleId(Long roleId);
    
    /**
     * 根据用户ID和角色ID删除关联
     * 
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_user_roles WHERE user_id = :userId AND role_id = :roleId")
    Mono<Void> deleteByUserIdAndRoleId(Long userId, Long roleId);
    
    /**
     * 根据用户ID删除所有角色关联
     * 
     * @param userId 用户ID
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_user_roles WHERE user_id = :userId")
    Mono<Void> deleteByUserId(Long userId);
    
    /**
     * 根据角色ID删除所有用户关联
     * 
     * @param roleId 角色ID
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_user_roles WHERE role_id = :roleId")
    Mono<Void> deleteByRoleId(Long roleId);
    
    /**
     * 检查用户是否拥有指定角色
     * 
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 是否拥有角色
     */
    @Query("SELECT COUNT(*) > 0 FROM t_user_roles WHERE user_id = :userId AND role_id = :roleId")
    Mono<Boolean> existsByUserIdAndRoleId(Long userId, Long roleId);
} 