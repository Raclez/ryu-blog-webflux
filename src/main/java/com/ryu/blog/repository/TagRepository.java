package com.ryu.blog.repository;

import com.ryu.blog.entity.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 标签存储库接口
 * @author ryu
 */
@Repository
public interface TagRepository extends R2dbcRepository<Tag, Long> {

    /**
     * 查询所有未删除的标签
     * 按名称排序提高用户体验
     * @return 标签列表
     */
    @Query("SELECT * FROM t_tags WHERE is_deleted = 0 ORDER BY name ASC")
    Flux<Tag> findAllTags();

    /**
     * 根据文章ID查询标签
     * 使用JOIN操作获取关联的标签数据
     * @param postId 文章ID
     * @return 标签列表
     */
    @Query("SELECT t.* FROM t_tags t " +
           "JOIN t_post_tags pt ON t.id = pt.tag_id " +
           "WHERE pt.post_id = :postId AND t.is_deleted = 0 " +
           "ORDER BY t.name ASC")
    Flux<Tag> findByPostId(Long postId);

    /**
     * 检查标签名称是否存在
     * 只查询未删除的标签以避免冲突
     * @param name 标签名称
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) FROM t_tags WHERE name = :name AND is_deleted = 0")
    Mono<Long> countByName(String name);

    /**
     * 获取标签的文章数量
     * 只统计已发布且未删除的文章
     * @param tagId 标签ID
     * @return 文章数量
     */
    @Query("SELECT COUNT(*) FROM t_post_tags pt " +
           "JOIN t_posts p ON pt.post_id = p.id " +
           "WHERE pt.tag_id = :tagId AND p.status = 1 AND p.is_deleted = 0")
    Mono<Long> countPostsByTagId(Long tagId);

    /**
     * 获取热门标签
     * 按文章数量排序，只包含有文章的标签
     * @param limit 限制数量
     * @return 标签列表
     */
    @Query("SELECT t.*, COUNT(pt.post_id) as post_count " +
           "FROM t_tags t " +
           "JOIN t_post_tags pt ON t.id = pt.tag_id " +
           "JOIN t_posts p ON pt.post_id = p.id " +
           "WHERE t.is_deleted = 0 AND p.status = 1 AND p.is_deleted = 0 " +
           "GROUP BY t.id " +
           "HAVING COUNT(pt.post_id) > 0 " +
           "ORDER BY post_count DESC, t.name ASC " +
           "LIMIT :limit")
    Flux<Tag> findHotTags(int limit);
    
    /**
     * 根据关键字查询标签
     * @param keyword 关键字
     * @param pageable 分页参数
     * @return 标签列表
     */
    @Query("SELECT * FROM t_tags WHERE is_deleted = 0 " +
           "AND (COALESCE(:keyword, '') = '' OR name LIKE CONCAT('%', :keyword, '%') " +
           "OR description LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY create_time DESC " +
           "LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Tag> findByKeyword(String keyword, Pageable pageable);
    
    /**
     * 统计符合条件的标签总数
     * @param keyword 关键字
     * @return 标签总数
     */
    @Query("SELECT COUNT(1) FROM t_tags WHERE is_deleted = 0 " +
           "AND (COALESCE(:keyword, '') = '' OR name LIKE CONCAT('%', :keyword, '%') " +
           "OR description LIKE CONCAT('%', :keyword, '%'))")
    Mono<Long> countByKeyword(String keyword);
} 