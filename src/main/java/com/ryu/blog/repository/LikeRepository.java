package com.ryu.blog.repository;

import com.ryu.blog.entity.Like;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 点赞存储库
 */
@Repository
public interface LikeRepository extends R2dbcRepository<Like, Long> {

    /**
     * 根据用户ID、类型和目标ID查询点赞记录
     *
     * @param userId   用户ID
     * @param type     类型
     * @param objectId 目标ID
     * @return 点赞记录
     */
    Mono<Like> findByUserIdAndTypeAndObjectIdAndIsDeleted(Long userId, Integer type, Long objectId, Integer isDeleted);

    /**
     * 根据类型和目标ID查询点赞记录
     *
     * @param type     类型
     * @param objectId 目标ID
     * @return 点赞记录列表
     */
    Flux<Like> findByTypeAndObjectIdAndIsDeleted(Integer type, Long objectId, Integer isDeleted);

    /**
     * 统计某个目标的点赞数
     *
     * @param type     类型
     * @param objectId 目标ID
     * @return 点赞数
     */
    Mono<Long> countByTypeAndObjectIdAndIsDeleted(Integer type, Long objectId, Integer isDeleted);

    /**
     * 根据用户ID查询点赞记录
     *
     * @param userId 用户ID
     * @return 点赞记录列表
     */
    Flux<Like> findByUserIdAndIsDeletedOrderByCreateTimeDesc(Long userId, Integer isDeleted);

    /**
     * 根据用户ID和类型查询点赞记录
     *
     * @param userId 用户ID
     * @param type   类型
     * @return 点赞记录列表
     */
    Flux<Like> findByUserIdAndTypeAndIsDeletedOrderByCreateTimeDesc(Long userId, Integer type, Integer isDeleted);

    /**
     * 批量获取多个目标的点赞数
     *
     * @param type      类型
     * @param objectIds 目标ID列表
     * @return 目标ID和点赞数的映射
     */
    @Query("SELECT target_id, COUNT(*) as count FROM t_like WHERE type = :type AND ObjectId IN (:targetIds) AND is_deleted = 0 GROUP BY ObjectId")
    Flux<Object[]> countByTypeAndObjectIdsAndIsDeleted(Integer type, Iterable<Long> objectIds);
} 