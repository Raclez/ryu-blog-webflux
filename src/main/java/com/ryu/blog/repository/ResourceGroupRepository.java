package com.ryu.blog.repository;

import com.ryu.blog.entity.ResourceGroup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 资源组数据库操作接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface ResourceGroupRepository extends R2dbcRepository<ResourceGroup, Long> {

    /**
     * 根据创建者ID查询资源组列表
     *
     * @param creatorId 创建者ID
     * @param isDeleted 是否删除（0-未删除，1-已删除）
     * @return 资源组列表
     */
    Flux<ResourceGroup> findByCreatorIdAndIsDeleted(Long creatorId, Integer isDeleted);

    /**
     * 根据组名查询资源组
     *
     * @param groupName 组名
     * @param isDeleted 是否删除（0-未删除，1-已删除）
     * @return 资源组
     */
    Mono<ResourceGroup> findByGroupNameAndIsDeleted(String groupName, Integer isDeleted);

    /**
     * 根据组名和创建者ID查询资源组
     *
     * @param groupName 组名
     * @param creatorId 创建者ID
     * @param isDeleted 是否删除（0-未删除，1-已删除）
     * @return 资源组
     */
    Mono<ResourceGroup> findByGroupNameAndCreatorIdAndIsDeleted(String groupName, Long creatorId, Integer isDeleted);

    /**
     * 统计指定状态的资源组数量
     *
     * @param isDeleted 是否删除（0-未删除，1-已删除）
     * @return 资源组数量
     */
    Mono<Long> countByIsDeleted(Integer isDeleted);

    /**
     * 分页查询资源组
     *
     * @param isDeleted 是否删除（0-未删除，1-已删除）
     * @param limit 分页大小
     * @param offset 偏移量
     * @return 资源组列表
     */
    @Query("SELECT * FROM t_resource_groups WHERE is_deleted = :isDeleted ORDER BY create_time DESC LIMIT :limit OFFSET :offset")
    Flux<ResourceGroup> findAllByIsDeletedOrderByCreateTimeDesc(Integer isDeleted, int limit, long offset);

    /**
     * 根据ID和状态查询资源组
     * 
     * @param id ID
     * @param isDeleted 是否删除（0-未删除，1-已删除）
     * @return 资源组
     */
    Mono<ResourceGroup> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * 根据关键字搜索资源组
     * 
     * @param keyword 关键字
     * @param isDeleted 是否删除（0-未删除，1-已删除）
     * @return 资源组列表
     */
    @Query("SELECT * FROM t_resource_groups WHERE is_deleted = :isDeleted AND (group_name LIKE CONCAT('%', :keyword, '%') OR description LIKE CONCAT('%', :keyword, '%'))")
    Flux<ResourceGroup> findByKeyword(String keyword, Integer isDeleted);

    /**
     * 计算指定关键字的资源组数量
     * 
     * @param keyword 关键字
     * @param isDeleted 是否删除（0-未删除，1-已删除）
     * @return 资源组数量
     */
    @Query("SELECT COUNT(1) FROM t_resource_groups WHERE is_deleted = :isDeleted AND (group_name LIKE CONCAT('%', :keyword, '%') OR description LIKE CONCAT('%', :keyword, '%'))")
    Mono<Long> countByKeyword(String keyword, Integer isDeleted);
} 