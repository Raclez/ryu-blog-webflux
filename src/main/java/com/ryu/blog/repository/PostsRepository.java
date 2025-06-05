package com.ryu.blog.repository;

import com.ryu.blog.entity.Posts;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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
     * @see t_posts 表的索引: idx_status_is_deleted_create_time
     */
    @Query("SELECT * FROM t_posts WHERE status = 1 AND is_deleted = 0 ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Posts> findPublishedPosts(Pageable pageable);

    /**
     * 查询已发布的文章总数
     * @return 文章总数
     * @see t_posts 表的索引: idx_status_is_deleted
     */
    @Query("SELECT COUNT(*) FROM t_posts WHERE status = 1 AND is_deleted = 0")
    Mono<Long> countPublishedPosts();

    /**
     * 根据分类ID查询已发布的文章列表
     * @param categoryId 分类ID
     * @param pageable 分页参数
     * @return 文章列表
     */
    @Query("SELECT p.* FROM t_posts p " +
           "JOIN t_post_categories pc ON p.id = pc.post_id " +
           "WHERE pc.category_id = :categoryId AND p.status = 1 AND p.is_deleted = 0 " +
           "ORDER BY p.create_time DESC " +
           "LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Posts> findByCategoryId(Long categoryId, Pageable pageable);

    /**
     * 根据分类ID查询已发布的文章总数
     * @param categoryId 分类ID
     * @return 文章总数
     */
    @Query("SELECT COUNT(*) FROM t_posts p " +
           "JOIN t_post_categories pc ON p.id = pc.post_id " +
           "WHERE pc.category_id = :categoryId AND p.status = 1 AND p.is_deleted = 0")
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
     * 直接更新文章浏览量为指定值
     * @param id 文章ID
     * @param views 新的浏览量值
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_posts SET views = :views WHERE id = :id")
    Mono<Integer> updateViews(Long id, int views);

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
    @Query("SELECT COUNT(DISTINCT a.id) FROM t_posts a " +
           "LEFT JOIN t_post_categories pc ON a.id = pc.post_id " +
           "LEFT JOIN t_post_tags pt ON a.id = pt.post_id " +
           "WHERE (:title IS NULL OR a.title LIKE CONCAT('%', :title, '%')) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:categoryId IS NULL OR pc.category_id = :categoryId) " +
           "AND (:tagId IS NULL OR pt.tag_id = :tagId) " +
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
    @Query("SELECT DISTINCT a.* FROM t_posts a " +
           "LEFT JOIN t_post_categories pc ON a.id = pc.post_id " +
           "LEFT JOIN t_post_tags pt ON a.id = pt.post_id " +
           "WHERE (:title IS NULL OR a.title LIKE CONCAT('%', :title, '%')) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:categoryId IS NULL OR pc.category_id = :categoryId) " +
           "AND (:tagId IS NULL OR pt.tag_id = :tagId) " +
           "AND (:startTime IS NULL OR a.create_time >= :startTime) " +
           "AND (:endTime IS NULL OR a.create_time <= :endTime) " +
           "AND a.is_deleted = 0 " +
           "ORDER BY a.create_time DESC " +
           "LIMIT :size OFFSET :page")
    Flux<Posts> findPostsByCondition(String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime, int page, int size);

    /**
     * 前台游标方式加载文章列表
     * 支持不同方向的文章加载（较新、较旧、综合）
     * @param cursor 游标ID
     * @param limit 每页大小
     * @param createTime 基准创建时间
     * @param direction 加载方向
     * @return 文章列表
     * @see t_posts 表的索引: idx_status_is_deleted_create_time, idx_id_status_is_deleted
     */
    @Query(value = 
        "SELECT a.* FROM t_posts a " +
        "WHERE a.status = 1 AND a.is_deleted = 0 " +
        "AND (" +
            "(:cursor IS NULL) OR " +
            "(:direction = 'newer' AND a.id > CAST(:cursor AS SIGNED)) OR " +
            "(:direction = 'older' AND a.id < CAST(:cursor AS SIGNED)) OR " +
            "(:direction = 'comprehensive')" +
        ") " +
        "AND (" +
            "(:createTime IS NULL) OR " +
            "(:direction = 'newer' AND a.create_time > :createTime) OR " +
            "(:direction = 'older' AND a.create_time < :createTime) OR " +
            "(:direction = 'comprehensive')" +
        ") " +
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
    @Query("SELECT p.* FROM t_posts p " +
           "JOIN t_post_categories pc ON p.id = pc.post_id " +
           "WHERE pc.category_id = :categoryId " +
           "AND p.id != :excludeId " +
           "AND p.status = 1 " +
           "AND p.is_deleted = 0 " +
           "ORDER BY p.create_time DESC " +
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
    
    /**
     * 批量查询文章及其分类信息
     * @param postIds 文章ID列表
     * @return 包含文章ID、分类ID和分类名称的结果
     */
    @Query("SELECT pc.post_id, pc.category_id, c.name as category_name " +
           "FROM t_post_categories pc " +
           "JOIN t_categories c ON pc.category_id = c.id " +
           "WHERE pc.post_id IN (:postIds)")
    Flux<Map<String, Object>> findPostsWithCategory(List<Long> postIds);
    
    /**
     * 批量查询文章的标签信息
     * @param postIds 文章ID列表
     * @return 包含文章ID、标签ID和标签名称的结果
     */
    @Query("SELECT pt.post_id, pt.tag_id, t.name as tag_name " +
           "FROM t_post_tags pt " +
           "JOIN t_tags t ON pt.tag_id = t.id " +
           "WHERE pt.post_id IN (:postIds)")
    Flux<Map<String, Object>> findPostsWithTags(List<Long> postIds);
    
    /**
     * 批量查询文章的评论数
     * @param postIds 文章ID列表
     * @return 包含文章ID和评论数的结果
     */
    @Query("SELECT post_id, COUNT(*) as comment_count " +
           "FROM t_comments " +
           "WHERE post_id IN (:postIds) AND is_deleted = 0 " +
           "GROUP BY post_id")
    Flux<Map<String, Object>> countCommentsByPostIds(List<Long> postIds);
} 