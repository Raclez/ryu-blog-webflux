package com.ryu.blog.repository;

import com.ryu.blog.entity.PostTag;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文章标签关联存储库接口
 * @author ryu
 */
@Repository
public interface PostTagRepository extends R2dbcRepository<PostTag, Long> {

    /**
     * 根据文章ID查询文章标签关联
     * @param postId 文章ID
     * @return 文章标签关联列表
     */
    Flux<PostTag> findByPostId(Long postId);

    /**
     * 根据标签ID查询文章标签关联
     * @param tagId 标签ID
     * @return 文章标签关联列表
     */
    Flux<PostTag> findByTagId(Long tagId);

    /**
     * 根据文章ID删除文章标签关联
     * @param postId 文章ID
     * @return 影响行数
     */
    @Modifying
    @Query("DELETE FROM t_post_tags WHERE post_id = :postId")
    Mono<Integer> deleteByPostId(Long postId);

    /**
     * 根据标签ID删除文章标签关联
     * @param tagId 标签ID
     * @return 影响行数
     */
    @Modifying
    @Query("DELETE FROM t_post_tags WHERE tag_id = :tagId")
    Mono<Integer> deleteByTagId(Long tagId);

    /**
     * 检查文章标签关联是否存在
     * @param postId 文章ID
     * @param tagId 标签ID
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) FROM t_post_tags WHERE post_id = :postId AND tag_id = :tagId")
    Mono<Long> countByPostIdAndTagId(Long postId, Long tagId);
} 