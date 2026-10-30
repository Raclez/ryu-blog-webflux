package com.ryu.blog.repository;

import com.ryu.blog.entity.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

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
     * 分页查询未删除的角色
     *
     * <p>排序固定为 {@code sort ASC, id ASC} —— {@code sort} 字段原先定义了却从没用过，
     * 而没有 {@code id} 兜底时同 sort 的角色顺序由 MySQL 自由决定，翻页会重复/漏项。
     *
     * @param pageable 分页参数
     * @return 角色列表
     */
    @Query("SELECT * FROM t_roles WHERE is_deleted = 0 ORDER BY sort ASC, id ASC "
            + "LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Role> findAllRoles(Pageable pageable);

    /**
     * 统计未删除的角色总数
     *
     * @return 角色总数
     */
    @Query("SELECT COUNT(*) FROM t_roles WHERE is_deleted = 0")
    Mono<Long> countAllRoles();

    /**
     * 按名称模糊分页查询角色
     *
     * @param name     角色名称
     * @param pageable 分页参数
     * @return 角色列表
     */
    @Query("SELECT * FROM t_roles WHERE is_deleted = 0 AND name LIKE CONCAT('%', :name, '%') "
            + "ORDER BY sort ASC, id ASC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Role> findByNameContaining(String name, Pageable pageable);

    /**
     * 统计按名称模糊匹配的角色数
     *
     * @param name 角色名称
     * @return 角色总数
     */
    @Query("SELECT COUNT(*) FROM t_roles WHERE is_deleted = 0 AND name LIKE CONCAT('%', :name, '%')")
    Mono<Long> countByNameContaining(String name);

    /**
     * 根据用户ID查询角色列表
     * @param userId 用户ID
     * @return 角色列表
     */
    @Query("SELECT r.* FROM t_roles r JOIN t_user_roles ur ON r.id = ur.role_id WHERE ur.user_id = :userId AND r.is_deleted = 0")
    Flux<Role> findByUserId(Long userId);

    /**
     * 查询用户拥有的、<b>处于启用状态</b>的角色
     *
     * <p>与 {@link #findByUserId(Long)} 的区别：这里额外过滤 {@code is_active = 1}。
     * 原先的权限判定链路只看 {@code t_user_roles → t_role_permissions}，
     * 完全绕过了 {@code t_roles}，导致「禁用角色」后该角色的权限仍然生效。
     *
     * @param userId 用户ID
     * @return 启用中的角色列表
     */
    @Query("SELECT r.* FROM t_roles r JOIN t_user_roles ur ON r.id = ur.role_id WHERE ur.user_id = :userId AND r.is_active = 1 AND r.is_deleted = 0")
    Flux<Role> findActiveRolesByUserId(Long userId);

    /**
     * 查询用户拥有的、启用中的角色ID
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @Query("SELECT r.id FROM t_roles r JOIN t_user_roles ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = :userId AND r.is_active = 1 AND r.is_deleted = 0")
    Flux<Long> findActiveRoleIdsByUserId(Long userId);

    /**
     * 按 ID 批量查询启用中的角色（用户列表一次补齐角色信息，避免 N+1）
     *
     * @param ids 角色ID集合（调用方必须保证非空）
     * @return 角色列表
     */
    @Query("SELECT * FROM t_roles WHERE id IN (:ids) AND is_active = 1 AND is_deleted = 0")
    Flux<Role> findActiveByIdIn(Collection<Long> ids);

    /**
     * 检查角色编码是否存在
     *
     * <p>MySQL 的 {@code COUNT(*) > 0} 返回整数，Spring Data 的 @Query 路径无法把它
     * 转成 Boolean（会报 "Failed to convert from type [MySqlDataRow] to type
     * [java.lang.Boolean]"），所以改成查计数、在 Java 侧转 boolean。
     *
     * @param code 角色编码
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) FROM t_roles WHERE code = :code AND is_deleted = 0")
    Mono<Long> countByCode(String code);

    default Mono<Boolean> existsByCode(String code) {
        return countByCode(code).map(count -> count != null && count > 0);
    }

    /**
     * 检查角色名称是否存在
     * @param name 角色名称
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) FROM t_roles WHERE name = :name AND is_deleted = 0")
    Mono<Long> countByName(String name);

    default Mono<Boolean> existsByName(String name) {
        return countByName(name).map(count -> count != null && count > 0);
    }
    
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

    /**
     * 查询默认角色
     *
     * <p>用显式 {@code @Query} 而不是派生查询 {@code findByIsDefaultAndIsDeleted}：
     * 派生查询会把 {@code Integer 1} 绑到 {@code Boolean} 类型的属性上，
     * 类型转换不可靠；而显式 SQL 没有这个歧义。
     *
     * <p>返回 {@code Flux} 而不是 {@code Mono}：数据库里若出现多个 {@code is_default = 1}，
     * 派生查询会抛 {@code IncorrectResultSizeDataAccessException}，
     * 这里交给调用方 {@code next()} 取第一个。
     *
     * @return 默认角色列表（按 id 升序）
     */
    @Query("SELECT * FROM t_roles WHERE is_default = 1 AND is_deleted = 0 ORDER BY id ASC")
    Flux<Role> findDefaultRoles();
} 