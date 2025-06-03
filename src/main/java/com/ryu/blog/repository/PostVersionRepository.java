package com.ryu.blog.repository;

import com.ryu.blog.entity.PostVersion;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文章版本存储库
 */
@Repository
public interface PostVersionRepository extends ReactiveCrudRepository<PostVersion, Long> {

    /**
     * 根据文章ID查询版本列表
     *
     * @param postId 文章ID
     * @param isDeleted 是否删除
     * @return 版本列表
     */
    Flux<PostVersion> findByPostIdAndIsDeletedOrderByVersionDesc(Long postId, Integer isDeleted);

    /**
     * 根据文章ID和版本号查询版本
     *
     * @param postId 文章ID
     * @param version   版本号
     * @param isDeleted 是否删除
     * @return 版本信息
     */
    Mono<PostVersion> findByPostIdAndVersionAndIsDeleted(Long postId, Integer version, Integer isDeleted);

    /**
     * 获取文章最新版本
     *
     * @param postId 文章ID
     * @param isDeleted 是否删除
     * @return 最新版本
     */
    @Query("SELECT * FROM t_post_versions WHERE post_id = :postId AND is_deleted = :isDeleted ORDER BY version DESC LIMIT 1")
    Mono<PostVersion> findLatestVersionByPostId(Long postId, Integer isDeleted);

    /**
     * 获取文章的最大版本号
     *
     * @param postId 文章ID
     * @return 最大版本号
     */
    @Query("SELECT MAX(version) FROM t_post_versions WHERE post_id = :postId")
    Mono<Integer> findMaxVersionByPostId(Long postId);

    /**
     * 根据用户ID查询版本列表
     *
     * @param Editor    用户ID
     * @param isDeleted 是否删除
     * @return 版本列表
     */
    Flux<PostVersion> findByEditorAndIsDeletedOrderByCreateTimeDesc(Long Editor, Integer isDeleted);

    /**
     * 分页查询文章版本列表
     *
     * @param postId 文章ID
     * @param isDeleted 是否删除
     * @param limit     限制数量
     * @param offset    偏移量
     * @return 版本列表
     */
    @Query("SELECT * FROM t_post_versions WHERE post_id = :postId AND is_deleted = :isDeleted ORDER BY version DESC LIMIT :limit OFFSET :offset")
    Flux<PostVersion> findByPostIdAndIsDeletedOrderByVersionDesc(Long postId, Integer isDeleted, int limit, long offset);

    /**
     * 统计文章版本数量
     *
     * @param postId 文章ID
     * @param isDeleted 是否删除
     * @return 版本数量
     */
    Mono<Long> countByPostIdAndIsDeleted(Long postId, Integer isDeleted);
} 