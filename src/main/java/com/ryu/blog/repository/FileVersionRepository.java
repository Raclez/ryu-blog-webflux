package com.ryu.blog.repository;

import com.ryu.blog.entity.FileVersion;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件版本数据访问接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface FileVersionRepository extends R2dbcRepository<FileVersion, Long> {

    /**
     * 根据文件ID查询所有版本，按版本号降序排列
     *
     * @param fileId 文件ID
     * @return 文件版本列表
     */
    @Query("SELECT * FROM t_file_versions WHERE file_id = :fileId ORDER BY version_number DESC")
    Flux<FileVersion> findByFileIdOrderByVersionNumberDesc(Long fileId);

    /**
     * 根据文件ID查询最新版本
     *
     * @param fileId 文件ID
     * @return 最新版本
     */
    @Query("SELECT * FROM t_file_versions WHERE file_id = :fileId AND is_current = 1")
    Mono<FileVersion> findCurrentVersionByFileId(Long fileId);

    /**
     * 根据文件ID和版本号查询版本
     *
     * @param fileId        文件ID
     * @param versionNumber 版本号
     * @return 文件版本
     */
    Mono<FileVersion> findByFileIdAndVersionNumber(Long fileId, Integer versionNumber);

    /**
     * 查询文件的版本数量
     *
     * @param fileId 文件ID
     * @return 版本数量
     */
    @Query("SELECT COUNT(*) FROM t_file_versions WHERE file_id = :fileId")
    Mono<Long> countByFileId(Long fileId);

    /**
     * 更新文件所有版本为非当前版本
     *
     * @param fileId 文件ID
     * @return 更新的记录数
     */
    @Query("UPDATE t_file_versions SET is_current = 0 WHERE file_id = :fileId")
    Mono<Integer> resetCurrentVersionsByFileId(Long fileId);

    /**
     * 设置指定版本为当前版本
     *
     * @param id 版本ID
     * @return 更新的记录数
     */
    @Query("UPDATE t_file_versions SET is_current = 1 WHERE id = :id")
    Mono<Integer> setCurrentVersion(Long id);

    /**
     * 根据文件ID删除所有版本
     *
     * @param fileId 文件ID
     * @return 删除的记录数
     */
    @Query("DELETE FROM t_file_versions WHERE file_id = :fileId")
    Mono<Integer> deleteByFileId(Long fileId);
} 