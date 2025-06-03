package com.ryu.blog.repository;

import com.ryu.blog.entity.Posts;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 博客文章存储库接口
 * @author ryu
 */
@Repository
public interface PostsRepository extends R2dbcRepository<Posts, Long> {

    /**
     * 查询已发布的文章列表
     * @param pageable 分页参数
     * @return 文章列表
     */
    @Query("SELECT * FROM t_posts WHERE status = 1 AND is_deleted = 0 ORDER BY is_top DESC, create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Posts> findPublishedPosts(Pageable pageable);

    /**
     * 查询已发布的文章总数
     * @return 文章总数
     */
    @Query("SELECT COUNT(*) FROM t_posts WHERE status = 1 AND is_deleted = 0")
    Mono<Long> countPublishedPosts();

    /**
     * 根据分类ID查询已发布的文章列表
     * @param categoryId 分类ID
     * @param pageable 分页参数
     * @return 文章列表
     */
    @Query("SELECT * FROM t_posts WHERE category_id = :categoryId AND status = 1 AND is_deleted = 0 ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Posts> findByCategoryId(Long categoryId, Pageable pageable);

    /**
     * 根据分类ID查询已发布的文章总数
     * @param categoryId 分类ID
     * @return 文章总数
     */
    @Query("SELECT COUNT(*) FROM t_posts WHERE category_id = :categoryId AND status = 1 AND is_deleted = 0")
    Mono<Long> countByCategoryId(Long categoryId);

    /**
     * 根据用户ID查询文章列表
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 文章列表
     */
    @Query("SELECT * FROM t_posts WHERE user_id = :userId AND is_deleted = 0 ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Posts> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据用户ID查询文章总数
     * @param userId 用户ID
     * @return 文章总数
     */
    @Query("SELECT COUNT(*) FROM t_posts WHERE user_id = :userId AND is_deleted = 0")
    Mono<Long> countByUserId(Long userId);

    /**
     * 增加文章访问量
     * @param id 文章ID
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_posts SET views = views + 1 WHERE id = :id")
    Mono<Integer> incrementViews(Long id);

    /**
     * 增加文章点赞数
     * @param id 文章ID
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_posts SET likes = likes + 1 WHERE id = :id")
    Mono<Integer> incrementLikes(Long id);

    /**
     * 增加文章评论数
     * @param id 文章ID
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_posts SET comments = comments + 1 WHERE id = :id")
    Mono<Integer> incrementComments(Long id);

    /**
     * 减少文章评论数
     * @param id 文章ID
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_posts SET comments = comments - 1 WHERE id = :id AND comments > 0")
    Mono<Integer> decrementComments(Long id);

    /**
     * 查询热门文章列表
     * @param limit 限制数量
     * @return 文章列表
     */
    @Query("SELECT * FROM t_posts WHERE status = 1 AND is_deleted = 0 ORDER BY views DESC LIMIT :limit")
    Flux<Posts> findHotPosts(int limit);

    /**
     * 根据条件查询文章总数
     * @param title 文章标题
     * @param status 文章状态
     * @param categoryId 分类ID
     * @param tagId 标签ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 文章总数
     */
    @Query("SELECT COUNT(*) FROM t_posts a LEFT JOIN t_post_tags at ON a.id = at.post_id " +
           "WHERE (:title IS NULL OR a.title LIKE CONCAT('%', :title, '%')) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:categoryId IS NULL OR a.category_id = :categoryId) " +
           "AND (:tagId IS NULL OR at.tag_id = :tagId) " +
           "AND (:startTime IS NULL OR a.create_time >= :startTime) " +
           "AND (:endTime IS NULL OR a.create_time <= :endTime) " +
           "AND a.is_deleted = 0")
    Mono<Long> countPostsByCondition(String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime);

    /**
     * 根据条件查询文章列表
     * @param title 文章标题
     * @param status 文章状态
     * @param categoryId 分类ID
     * @param tagId 标签ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页大小
     * @return 文章列表
     */
    @Query("SELECT DISTINCT a.* FROM t_posts a LEFT JOIN t_post_tags at ON a.id = at.post_id " +
           "WHERE (:title IS NULL OR a.title LIKE CONCAT('%', :title, '%')) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:categoryId IS NULL OR a.category_id = :categoryId) " +
           "AND (:tagId IS NULL OR at.tag_id = :tagId) " +
           "AND (:startTime IS NULL OR a.create_time >= :startTime) " +
           "AND (:endTime IS NULL OR a.create_time <= :endTime) " +
           "AND a.is_deleted = 0 " +
           "ORDER BY a.is_top DESC, a.create_time DESC " +
           "LIMIT :size OFFSET :page")
    Flux<Posts> findPostsByCondition(String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime, int page, int size);

    /**
     * 前台游标方式加载文章列表
     * @param cursor 游标ID
     * @param limit 每页大小
     * @param createTime 基准创建时间
     * @param direction 加载方向
     * @return 文章列表
     */
    @Query(value = 
        "SELECT a.* FROM t_posts a " +
        "WHERE a.status = 1 AND a.is_deleted = 0 " +
        "AND (:cursor IS NULL OR " +
            "(:direction = 'newer' AND a.id > :cursor) OR " +
            "(:direction = 'older' AND a.id < :cursor) OR " +
            "(:direction = 'comprehensive')) " +
        "AND (:createTime IS NULL OR " +
            "(:direction = 'newer' AND a.create_time > :createTime) OR " +
            "(:direction = 'older' AND a.create_time < :createTime) OR " +
            "(:direction = 'comprehensive')) " +
        "ORDER BY " +
            "CASE WHEN :direction = 'newer' THEN a.create_time END ASC, " +
            "CASE WHEN :direction = 'older' OR :direction = 'comprehensive' THEN a.create_time END DESC " +
        "LIMIT :limit")
    Flux<Posts> findFrontPosts(String cursor, int limit, String createTime, String direction);

    /**
     * 获取相关文章（同分类）
     * @param categoryId 分类ID
     * @param excludeId 排除的文章ID
     * @param limit 限制数量
     * @return 相关文章列表
     */
    @Query("SELECT * FROM t_posts " +
           "WHERE category_id = :categoryId " +
           "AND id != :excludeId " +
           "AND status = 1 " +
           "AND is_deleted = 0 " +
           "ORDER BY create_time DESC " +
           "LIMIT :limit")
    Flux<Posts> findRelatedPostsByCategory(Long categoryId, Long excludeId, Integer limit);
    
    /**
     * 获取上一篇文章
     * @param id 当前文章ID
     * @return 上一篇文章
     */
    @Query("SELECT * FROM t_posts " +
           "WHERE id < :id " +
           "AND status = 1 " +
           "AND is_deleted = 0 " +
           "ORDER BY id DESC " +
           "LIMIT 1")
    Mono<Posts> findPrevPost(Long id);
    
    /**
     * 获取下一篇文章
     * @param id 当前文章ID
     * @return 下一篇文章
     */
    @Query("SELECT * FROM t_posts " +
           "WHERE id > :id " +
           "AND status = 1 " +
           "AND is_deleted = 0 " +
           "ORDER BY id ASC " +
           "LIMIT 1")
    Mono<Posts> findNextPost(Long id);
} 