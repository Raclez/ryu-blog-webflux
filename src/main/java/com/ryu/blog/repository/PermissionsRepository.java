package com.ryu.blog.repository;

import com.ryu.blog.entity.Permissions;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 权限仓库接口
 * 提供对权限数据的访问操作
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Repository
public interface PermissionsRepository extends R2dbcRepository<Permissions, Long> {

    /**
     * 根据权限标识查询权限
     *
     * @param identity 权限标识
     * @return 权限对象
     */
    Mono<Permissions> findByIdentity(String identity);

    /**
     * 根据ID列表查询权限
     *
     * @param ids 权限ID列表
     * @return 权限列表
     */
    Flux<Permissions> findByIdIn(List<Long> ids);

    /**
     * 根据权限标识列表查询权限
     *
     * @param identities 权限标识列表
     * @return 权限列表
     */
    Flux<Permissions> findByIdentityIn(List<String> identities);
    
    /**
     * 查询所有启用的权限
     *
     * @return 权限列表
     */
    @Query("SELECT * FROM t_permissions WHERE is_active = 1 AND is_deleted = 0")
    Flux<Permissions> findAllEnabled();
    
    /**
     * 根据权限标识前缀查询权限
     *
     * @param prefix 权限标识前缀，如 "system:", "content:"
     * @param isDeleted 是否删除标志
     * @return 权限列表
     */
    @Query("SELECT * FROM t_permissions WHERE identity LIKE CONCAT(:prefix, '%') AND is_deleted = :isDeleted ORDER BY id ASC")
    Flux<Permissions> findByIdentityStartingWithAndIsDeletedOrderByIdAsc(String prefix, Integer isDeleted);
    
    /**
     * 根据权限标识前缀查询权限（带分页）
     *
     * @param prefix 权限标识前缀，如 "system:", "content:"
     * @param isDeleted 是否删除标志
     * @param pageable 分页参数
     * @return 权限列表
     */
    @Query("SELECT * FROM t_permissions WHERE identity LIKE CONCAT(:prefix, '%') AND is_deleted = :isDeleted ORDER BY id ASC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Permissions> findByIdentityStartingWithAndIsDeletedOrderByIdAsc(String prefix, Integer isDeleted, Pageable pageable);
    
    /**
     * 统计符合权限标识前缀的权限数量
     *
     * @param prefix 权限标识前缀
     * @param isDeleted 是否删除标志
     * @return 权限数量
     */
    @Query("SELECT COUNT(*) FROM t_permissions WHERE identity LIKE CONCAT(:prefix, '%') AND is_deleted = :isDeleted")
    Mono<Long> countByIdentityStartingWithAndIsDeleted(String prefix, Integer isDeleted);
    
    /**
     * 根据权限标识前缀查询权限
     *
     * @param prefix 权限标识前缀
     * @return 权限列表
     */
    Flux<Permissions> findByIdentityStartingWith(String prefix);
    
    /**
     * 查询所有未删除的权限
     *
     * @param isDeleted 是否删除标志
     * @return 权限列表
     */
    Flux<Permissions> findByIsDeletedOrderByIdAsc(Integer isDeleted);
    
    /**
     * 查询所有未删除的权限（带分页）
     *
     * @param isDeleted 是否删除标志
     * @param pageable 分页参数
     * @return 权限列表
     */
    @Query("SELECT * FROM t_permissions WHERE is_deleted = :isDeleted ORDER BY id ASC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Permissions> findByIsDeletedOrderByIdAsc(Integer isDeleted, Pageable pageable);
    
    /**
     * 统计所有未删除的权限数量
     *
     * @param isDeleted 是否删除标志
     * @return 权限数量
     */
    @Query("SELECT COUNT(*) FROM t_permissions WHERE is_deleted = :isDeleted")
    Mono<Long> countByIsDeleted(Integer isDeleted);
} 