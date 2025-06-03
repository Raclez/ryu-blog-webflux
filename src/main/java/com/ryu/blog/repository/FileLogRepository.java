package com.ryu.blog.repository;

import com.ryu.blog.entity.FileLog;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 文件日志数据访问接口
 *
 * @author ryu 475118582@qq.com
 */
@Repository
public interface FileLogRepository extends R2dbcRepository<FileLog, Long> {

    /**
     * 根据文件ID查询日志，按时间倒序排列
     *
     * @param fileId 文件ID
     * @return 文件日志列表
     */
    @Query("SELECT * FROM t_file_logs WHERE file_id = :fileId ORDER BY timestamp DESC")
    Flux<FileLog> findByFileIdOrderByTimestampDesc(Long fileId);

    /**
     * 根据用户ID查询日志，按时间倒序排列
     *
     * @param userId 用户ID
     * @return 文件日志列表
     */
    @Query("SELECT * FROM t_file_logs WHERE user_id = :userId ORDER BY timestamp DESC")
    Flux<FileLog> findByUserIdOrderByTimestampDesc(Long userId);

    /**
     * 根据操作类型查询日志，按时间倒序排列
     *
     * @param operation 操作类型
     * @return 文件日志列表
     */
    @Query("SELECT * FROM t_file_logs WHERE operation = :operation ORDER BY timestamp DESC")
    Flux<FileLog> findByOperationOrderByTimestampDesc(String operation);

    /**
     * 根据时间范围查询日志，按时间倒序排列
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 文件日志列表
     */
    @Query("SELECT * FROM t_file_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    Flux<FileLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据文件ID和操作类型查询日志，按时间倒序排列
     *
     * @param fileId    文件ID
     * @param operation 操作类型
     * @return 文件日志列表
     */
    @Query("SELECT * FROM t_file_logs WHERE file_id = :fileId AND operation = :operation ORDER BY timestamp DESC")
    Flux<FileLog> findByFileIdAndOperationOrderByTimestampDesc(Long fileId, String operation);

    /**
     * 根据文件ID删除所有日志
     *
     * @param fileId 文件ID
     * @return 删除的记录数
     */
    @Query("DELETE FROM t_file_logs WHERE file_id = :fileId")
    Mono<Integer> deleteByFileId(Long fileId);

    /**
     * 根据用户ID删除所有日志
     *
     * @param userId 用户ID
     * @return 删除的记录数
     */
    @Query("DELETE FROM t_file_logs WHERE user_id = :userId")
    Mono<Integer> deleteByUserId(Long userId);
} 