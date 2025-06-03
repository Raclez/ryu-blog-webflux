package com.ryu.blog.repository;

import com.ryu.blog.entity.File;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件数据库操作接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface FileRepository extends R2dbcRepository<File, Long> {

    /**
     * 根据创建者ID查询文件列表
     *
     * @param creatorId 创建者ID
     * @param isDeleted 是否删除
     * @return 文件列表
     */
    Flux<File> findByCreatorIdAndIsDeleted(Long creatorId, Integer isDeleted);

    /**
     * 根据ID和创建者ID查询文件
     *
     * @param id        文件ID
     * @param creatorId 创建者ID
     * @param isDeleted 是否删除
     * @return 文件信息
     */
    Mono<File> findByIdAndCreatorIdAndIsDeleted(Long id, Long creatorId, Integer isDeleted);

    /**
     * 根据ID查询未删除的文件
     *
     * @param id 文件ID
     * @param isDeleted 是否删除
     * @return 文件信息
     */
    Mono<File> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * 分页查询文件列表
     *
     * @param isDeleted 是否删除
     * @param pageable  分页参数
     * @return 文件列表
     */
    @Query("SELECT f.* FROM t_file f WHERE f.is_deleted = :isDeleted ORDER BY f.create_time DESC LIMIT :limit OFFSET :offset")
    Flux<File> findAllByIsDeletedOrderByCreateTimeDesc(Integer isDeleted, int limit, long offset);

    /**
     * 统计未删除的文件数量
     *
     * @param isDeleted 是否删除
     * @return 文件数量
     */
    Mono<Long> countByIsDeleted(Integer isDeleted);

    /**
     * 根据文件类型查询文件列表
     *
     * @param fileType  文件类型
     * @param isDeleted 是否删除
     * @return 文件列表
     */
    Flux<File> findByFileTypeAndIsDeletedOrderByCreateTimeDesc(String fileType, Integer isDeleted);

    /**
     * 根据文件类型查询文件列表
     *
     * @param fileType 文件类型
     * @param isDeleted 是否删除
     * @return 文件列表
     */
    Flux<File> findByFileTypeAndIsDeleted(String fileType, Integer isDeleted);

    /**
     * 根据文件路径查询文件
     *
     * @param filePath 文件路径
     * @return 文件
     */
    Mono<File> findByFilePath(String filePath);

    /**
     * 根据文件校验和查询文件
     *
     * @param checksum 文件校验和
     * @param isDeleted 是否删除
     * @return 文件
     */
    Mono<File> findFirstByChecksumAndIsDeleted(String checksum, Integer isDeleted);

    /**
     * 根据校验和查询文件列表
     *
     * @param checksum 文件校验和
     * @param isDeleted 是否删除
     * @return 文件列表
     */
    Flux<File> findByChecksumAndIsDeleted(String checksum, Integer isDeleted);
} 