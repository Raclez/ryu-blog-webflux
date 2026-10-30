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
 * <p>动态条件 / 动态排序 / 分页这类无法用派生查询表达的查询，
 * 统一放在 {@link FileRepositoryCustom} 片段里，由
 * {@link FileRepositoryCustomImpl} 实现，保证 Repository 是唯一的数据访问入口。
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface FileRepository extends R2dbcRepository<File, Long>, FileRepositoryCustom {

    /**
     * 根据创建者ID查询文件列表
     *
     * @param creatorId 创建者ID
     * @param isDeleted 是否删除
     * @return 文件列表
     */
    @Query("SELECT f.* FROM t_file f WHERE f.creator_id = :creatorId AND f.is_deleted = :isDeleted "
            + "ORDER BY f.create_time DESC, f.id DESC")
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
    @Query("SELECT f.* FROM t_file f WHERE f.is_deleted = :isDeleted "
            + "ORDER BY f.create_time DESC, f.id DESC LIMIT :limit OFFSET :offset")
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
    @Query("SELECT f.* FROM t_file f WHERE f.file_type = :fileType AND f.is_deleted = :isDeleted "
            + "ORDER BY f.create_time DESC, f.id DESC")
    Flux<File> findByFileTypeAndIsDeletedOrderByCreateTimeDesc(String fileType, Integer isDeleted);

    /**
     * 根据文件类型查询文件列表
     *
     * @param fileType 文件类型
     * @param isDeleted 是否删除
     * @return 文件列表
     */
    @Query("SELECT f.* FROM t_file f WHERE f.file_type = :fileType AND f.is_deleted = :isDeleted "
            + "ORDER BY f.create_time DESC, f.id DESC")
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
     * 按「校验和 + 上传者」查询文件
     *
     * <p>去重必须限定在当前用户范围内：按校验和全局查会把**别人的**文件信息
     * （文件名、公开 URL）返回给调用方 —— 只要知道内容就能读到元数据，
     * 构成跨用户信息泄露。
     *
     * @param checksum 校验和
     * @param creatorId 上传者ID
     * @param isDeleted 删除标记
     * @return 文件
     */
    Mono<File> findFirstByChecksumAndCreatorIdAndIsDeleted(String checksum, Long creatorId, Integer isDeleted);

    /**
     * 根据校验和查询文件列表
     *
     * @param checksum 文件校验和
     * @param isDeleted 是否删除
     * @return 文件列表
     */
    Flux<File> findByChecksumAndIsDeleted(String checksum, Integer isDeleted);
} 