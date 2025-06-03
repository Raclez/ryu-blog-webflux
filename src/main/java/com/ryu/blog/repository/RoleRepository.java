package com.ryu.blog.repository;

import com.ryu.blog.entity.Role;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 角色存储库接口
 * @author ryu
 */
@Repository
public interface RoleRepository extends R2dbcRepository<Role, Long> {

    /**
     * 根据角色编码查询角色
     * @param code 角色编码
     * @return 角色信息
     */
    Mono<Role> findByCode(String code);

    /**
     * 查询所有未删除的角色
     * @return 角色列表
     */
    @Query("SELECT * FROM t_roles WHERE is_deleted = 0")
    Flux<Role> findAllRoles();

    /**
     * 根据用户ID查询角色列表
     * @param userId 用户ID
     * @return 角色列表
     */
    @Query("SELECT r.* FROM t_roles r JOIN t_user_roles ur ON r.id = ur.role_id WHERE ur.user_id = :userId AND r.is_deleted = 0")
    Flux<Role> findByUserId(Long userId);

    /**
     * 检查角色编码是否存在
     * @param code 角色编码
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM t_roles WHERE code = :code AND is_deleted = 0")
    Mono<Boolean> existsByCode(String code);

    /**
     * 检查角色名称是否存在
     * @param name 角色名称
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM t_roles WHERE name = :name AND is_deleted = 0")
    Mono<Boolean> existsByName(String name);
    
    /**
     * 根据名称模糊查询角色
     * @param name 角色名称
     * @param isDeleted 是否删除标识
     * @return 角色列表
     */
    @Query("SELECT * FROM t_roles WHERE name LIKE CONCAT('%', :name, '%') AND is_deleted = :isDeleted")
    Flux<Role> findByNameContainingAndIsDeleted(String name, Integer isDeleted);
    
    /**
     * 根据是否为默认角色查询
     * @param isDefault 是否默认角色
     * @param isDeleted 是否删除标识
     * @return 角色信息
     */
    Mono<Role> findByIsDefaultAndIsDeleted(Integer isDefault, Integer isDeleted);
} 