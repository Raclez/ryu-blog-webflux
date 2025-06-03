package com.ryu.blog.repository;

import com.ryu.blog.entity.FilePermission;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 文件权限数据访问接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface FilePermissionRepository extends R2dbcRepository<FilePermission, Long> {

    /**
     * 根据文件ID查询所有权限
     *
     * @param fileId 文件ID
     * @return 文件权限列表
     */
    Flux<FilePermission> findByFileId(Long fileId);

    /**
     * 根据用户ID查询所有权限
     *
     * @param userId 用户ID
     * @return 文件权限列表
     */
    Flux<FilePermission> findByUserId(Long userId);

    /**
     * 根据文件ID和用户ID查询权限
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件权限
     */
    Mono<FilePermission> findByFileIdAndUserId(Long fileId, Long userId);

    /**
     * 根据文件ID和授权人ID查询权限
     *
     * @param fileId    文件ID
     * @param grantorId 授权人ID
     * @return 文件权限列表
     */
    Flux<FilePermission> findByFileIdAndGrantorId(Long fileId, Long grantorId);

    /**
     * 查询用户对文件的有效权限（未过期或永不过期）
     *
     * @param fileId    文件ID
     * @param userId    用户ID
     * @param currentTime 当前时间
     * @return 文件权限
     */
    @Query("SELECT * FROM t_file_permissions WHERE file_id = :fileId AND user_id = :userId AND (expire_time IS NULL OR expire_time > :currentTime)")
    Mono<FilePermission> findValidPermission(Long fileId, Long userId, LocalDateTime currentTime);

    /**
     * 根据文件ID删除所有权限
     *
     * @param fileId 文件ID
     * @return 删除的记录数
     */
    @Query("DELETE FROM t_file_permissions WHERE file_id = :fileId")
    Mono<Integer> deleteByFileId(Long fileId);

    /**
     * 根据用户ID删除所有权限
     *
     * @param userId 用户ID
     * @return 删除的记录数
     */
    @Query("DELETE FROM t_file_permissions WHERE user_id = :userId")
    Mono<Integer> deleteByUserId(Long userId);

    /**
     * 根据授权人ID删除所有权限
     *
     * @param grantorId 授权人ID
     * @return 删除的记录数
     */
    @Query("DELETE FROM t_file_permissions WHERE grantor_id = :grantorId")
    Mono<Integer> deleteByGrantorId(Long grantorId);

    /**
     * 根据过期时间查找已过期的权限
     *
     * @param currentTime 当前时间
     * @return 已过期的权限列表
     */
    @Query("SELECT * FROM t_file_permissions WHERE expire_time IS NOT NULL AND expire_time <= :currentTime")
    Flux<FilePermission> findExpiredPermissions(LocalDateTime currentTime);

    /**
     * 删除已过期的权限
     *
     * @param currentTime 当前时间
     * @return 删除的记录数
     */
    @Query("DELETE FROM t_file_permissions WHERE expire_time IS NOT NULL AND expire_time <= :currentTime")
    Mono<Integer> deleteExpiredPermissions(LocalDateTime currentTime);
} 