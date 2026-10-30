package com.ryu.blog.repository;

import com.ryu.blog.entity.ResourceGroupFileRel;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 资源组文件关联操作接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface ResourceGroupFileRelRepository extends R2dbcRepository<ResourceGroupFileRel, Long> {

    /**
     * 根据组ID和文件ID查询关联记录
     *
     * @param groupId 组ID
     * @param fileId 文件ID
     * @return 关联记录
     */
    Mono<ResourceGroupFileRel> findByGroupIdAndFileId(Long groupId, Long fileId);

    /**
     * 根据组ID查询关联记录
     *
     * @param groupId 组ID
     * @return 关联记录列表
     */
    Flux<ResourceGroupFileRel> findByGroupId(Long groupId);
    
    /**
     * 根据文件ID查询关联记录
     *
     * @param fileId 文件ID
     * @return 关联记录列表
     */
    Flux<ResourceGroupFileRel> findByFileId(Long fileId);
    
    /**
     * 删除组ID和文件ID的关联记录
     *
     * @param groupId 组ID
     * @param fileId 文件ID
     * @return 删除结果
     */
    Mono<Void> deleteByGroupIdAndFileId(Long groupId, Long fileId);
    
    /**
     * 删除组ID的所有关联记录
     *
     * @param groupId 组ID
     * @return 删除结果
     */
    Mono<Void> deleteByGroupId(Long groupId);
    
    /**
     * 删除文件ID的所有关联记录
     *
     * @param fileId 文件ID
     * @return 删除结果
     */
    Mono<Void> deleteByFileId(Long fileId);
    
    /**
     * 获取指定资源组中的文件数量
     *
     * @param groupId 资源组ID
     * @return 文件数量
     */
    Mono<Long> countByGroupId(Long groupId);
    
    /**
     * 获取指定文件所属的资源组数量
     *
     * @param fileId 文件ID
     * @return 资源组数量
     */
    Mono<Long> countByFileId(Long fileId);
    
    /**
     * 分页查询资源组中的文件ID
     *
     * @param groupId 资源组ID
     * @param limit 分页大小
     * @param offset 偏移量
     * @return 文件ID列表
     */
    @Query("SELECT file_id FROM t_resource_group_file_rels WHERE group_id = :groupId ORDER BY create_time DESC LIMIT :limit OFFSET :offset")
    Flux<Long> findFileIdsByGroupId(Long groupId, int limit, long offset);
    
    /**
     * 分页查询所有文件ID（不按资源组过滤）
     *
     * <p><b>为什么不是 {@code SELECT DISTINCT ... ORDER BY create_time}</b>：
     * MySQL 8 在 {@code DISTINCT} 下要求 {@code ORDER BY} 的表达式出现在
     * {@code SELECT} 列表里，否则报 {@code ERROR 3065}：
     * {@code Expression #1 of ORDER BY clause is not in SELECT list}。
     *
     * <p>改用 {@code GROUP BY file_id} + {@code ORDER BY MAX(create_time)}：
     * 语义与 {@code DISTINCT} 一致（每个 file_id 一行），排序取该文件<b>最近一次</b>
     * 关联的时间 —— 这也正是原来 {@code DISTINCT + ORDER BY create_time} 想表达的意思。
     *
     * @param limit 分页大小
     * @param offset 偏移量
     * @return 文件ID列表
     */
    @Query("SELECT file_id FROM t_resource_group_file_rels GROUP BY file_id ORDER BY MAX(create_time) DESC LIMIT :limit OFFSET :offset")
    Flux<Long> findAllFileIds(int limit, long offset);
    
    /**
     * 获取所有文件的数量（去重）
     *
     * @return 文件数量
     */
    @Query("SELECT COUNT(DISTINCT file_id) FROM t_resource_group_file_rels")
    Mono<Long> countAllFiles();
} 