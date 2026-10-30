package com.ryu.blog.repository;

import com.ryu.blog.dto.PostAccessPasswordProjection;
import com.ryu.blog.dto.PostCategoryProjection;
import com.ryu.blog.dto.PostTagProjection;
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
 *
 * <p><b>公开查询的可见性规则</b>：凡是「前台可见」的查询（列表 / 计数 / 上下篇 / 热门 / 游标分页），
 * 都必须同时带三个条件，缺一不可：
 * <pre>
 *   status = 1                              -- 已发布
 *   AND is_deleted = 0                      -- 未删除
 *   AND (visibility IS NULL OR visibility = 'public')   -- 非 private / password
 * </pre>
 *
 * <p>漏掉 {@code visibility} 会把 private 文章的标题/摘要列到公开列表里；
 * 漏掉 {@code status} 会把草稿直接列出来（以前 {@code findByUserId} 就是这样）。
 * 为了避免以后新增查询时漏写，三个条件里的可见性谓词统一用下面的常量。
 *
 * <p>管理端查询（{@code findPostsByCondition}）是刻意不受此限的 ——
 * 管理端就是要看到全部状态。
 *
 * @author ryu
 */
@Repository
public interface PostsRepository extends R2dbcRepository<Posts, Long> {

    /**
     * 公开可见性谓词（无表别名）
     */
    String PUBLIC_VISIBILITY = "(visibility IS NULL OR visibility = 'public')";

    /**
     * 公开可见性谓词（表别名 p）
     */
    String PUBLIC_VISIBILITY_P = "(p.visibility IS NULL OR p.visibility = 'public')";

    /**
     * 公开可见性谓词（表别名 a）
     */
    String PUBLIC_VISIBILITY_A = "(a.visibility IS NULL OR a.visibility = 'public')";

    /**
     * 按ID批量查询文章
     * <p>
     * 用于消除「列表里每行再查一次详情」的 N+1 查询。
     *
     * <p><b>已软删除的文章不返回</b>：这个方法唯一的调用方是「我的浏览历史」，
     * 如果把软删除的文章也查出来，它的标题/封面会顺着浏览历史接口漏出去 ——
     * 文章都删了，标题却还读得到。
     *
     * @param ids 文章ID列表
     * @return 文章列表
     */
    @Query("SELECT * FROM t_posts WHERE id IN (:ids) AND is_deleted = 0")
    Flux<Posts> findByIdIn(Iterable<Long> ids);

    /**
     * 统计占用某个别名的文章数（排除指定文章自身）
     *
     * <p>别名（slug）是文章的 URL 标识，必须唯一。DTO 一直声明了 slug、
     * {@code BusinessException.postSlugExists()} 也早就写好了，但从来没有真正校验过，
     * 结果是谁都能占用同一个别名。
     *
     * <p><b>为什么不用派生查询</b>：原先写的是派生方法
     * {@code countBySlugAndIsDeletedAndIdNot(slug, isDeleted, excludeId)}，
     * 创建时 {@code excludeId} 传 null —— 而 Spring Data R2DBC 的派生查询
     * 会拒绝 null 参数（{@code ParameterMetadataProvider.checkNullIsAllowed}），
     * 于是**任何带别名的创建请求都会抛
     * {@code IllegalArgumentException: Value of parameter with name id must not be null}**。
     * 派生查询也无法表达「排除自身」这个条件在 excludeId 为空时的语义。
     * 这里改用显式 {@code @Query}，用 {@code (:excludeId IS NULL OR id <> :excludeId)}
     * 同时覆盖「创建（excludeId 为空）」与「编辑（排除自身）」两种情况 ——
     * 同一个写法在本文件里已用于 {@code countPostsByCondition} 的可选条件。
     *
     * <p><b>刻意不按 {@code is_deleted} 过滤</b>：{@code t_posts.slug} 上有唯一键
     * {@code uk_posts_slug}，它对**软删除的行一样生效** ——
     * 若这里放过软删除的行，下一步 INSERT 就会撞唯一键炸成 500。
     * 所以这里统计全部行，与数据库的约束口径保持一致。
     *
     * @param slug 别名
     * @param excludeId 排除的文章ID（创建时传 null）
     * @return 占用数
     */
    @Query("SELECT COUNT(*) FROM t_posts WHERE slug = :slug AND (:excludeId IS NULL OR id <> :excludeId)")
    Mono<Long> countSlugUsage(String slug, Long excludeId);

    /**
     * 找出「访问密码还是明文」的历史行
     *
     * <p>2026-10-30 之前，文章访问密码是明文存进 {@code t_posts.password} 的。
     * 新写入的密码一律走 BCrypt（见 {@code ArticleAccessPasswordPolicy}），
     * 但历史行还留在库里 —— 这个查询把它们捞出来洗成哈希。
     *
     * <p>{@code NOT LIKE '$2%'}：BCrypt 哈希串固定以 {@code $2} 开头，
     * 不满足这个前缀的就是历史明文（{@code $} 在 MySQL 的 LIKE 里没有特殊含义）。
     *
     * @return 明文密码的历史行
     */
    @Query("SELECT id, password FROM t_posts " +
           "WHERE password IS NOT NULL AND password <> '' AND password NOT LIKE '$2%'")
    Flux<PostAccessPasswordProjection> findLegacyPlaintextAccessPasswords();

    /**
     * 改写文章的访问密码
     *
     * <p>用于把历史明文洗成 BCrypt 哈希 —— 口令本身不变，文章照样能用同一个口令打开。
     *
     * @param id       文章ID
     * @param password 新的（哈希后的）访问密码
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE t_posts SET password = :password WHERE id = :id")
    Mono<Integer> updateAccessPassword(Long id, String password);

    /**
     * 查询已发布的文章列表
     * @param pageable 分页参数
     * @return 文章列表
     * @see t_posts 表的索引: idx_status_is_deleted_create_time
     */
    @Query("SELECT * FROM t_posts WHERE status = 1 AND is_deleted = 0 " +
           "AND " + PUBLIC_VISIBILITY + " " +
           "ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Posts> findPublishedPosts(Pageable pageable);

    /**
     * 查询已发布的文章总数
     * @return 文章总数
     * @see t_posts 表的索引: idx_status_is_deleted
     */
    @Query("SELECT COUNT(*) FROM t_posts WHERE status = 1 AND is_deleted = 0 " +
           "AND " + PUBLIC_VISIBILITY + "")
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
           "AND " + PUBLIC_VISIBILITY_P + " " +
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
           "WHERE pc.category_id = :categoryId AND p.status = 1 AND p.is_deleted = 0 " +
           "AND " + PUBLIC_VISIBILITY_P + "")
    Mono<Long> countByCategoryId(Long categoryId);

    /**
     * 根据用户ID查询文章列表
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 文章列表
     */
    @Query("SELECT * FROM t_posts WHERE user_id = :userId AND status = 1 AND is_deleted = 0 " +
           "AND " + PUBLIC_VISIBILITY + " " +
           "ORDER BY create_time DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<Posts> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据用户ID查询文章总数
     * @param userId 用户ID
     * @return 文章总数
     */
    @Query("SELECT COUNT(*) FROM t_posts WHERE user_id = :userId AND status = 1 AND is_deleted = 0 " +
           "AND " + PUBLIC_VISIBILITY + "")
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
     * 在现有浏览量上累加增量
     * <p>
     * 浏览量在缓存里按增量累计、定时回写数据库，
     * 回写时必须用累加而不是覆盖，否则会把文章的历史浏览量清零。
     *
     * @param id    文章ID
     * @param delta 增量
     * @return 更新结果
     */
    @Modifying
    @Query("UPDATE t_posts SET views = views + :delta WHERE id = :id")
    Mono<Integer> addViews(Long id, int delta);

    /**
     * 批量获取文章的浏览量
     *
     * <p>已软删除的文章不返回 —— 文章都没了，浏览量不该还挂着。
     *
     * @param ids 文章ID列表
     * @return 文章ID与浏览量的映射
     */
    @Query("SELECT id, views FROM t_posts WHERE id IN (:ids) AND is_deleted = 0")
    Flux<Object[]> findViewsByIds(Iterable<Long> ids);

    /**
     * 查询热门文章列表
     * @param limit 限制数量
     * @return 文章列表
     */
    @Query("SELECT * FROM t_posts WHERE status = 1 AND is_deleted = 0 " +
           "AND " + PUBLIC_VISIBILITY + " ORDER BY views DESC LIMIT :limit")
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
           // 置顶优先：后台列表按置顶 → 创建时间排序，与前台的排序口径一致
           "ORDER BY a.is_sticky DESC, a.create_time DESC " +
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
        "AND " + PUBLIC_VISIBILITY_A + " " +
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
            // 置顶优先：置顶文章排在最前，其余按时间排序
            "a.is_sticky DESC, " +
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
           "AND " + PUBLIC_VISIBILITY_P + " " +
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
           "AND " + PUBLIC_VISIBILITY + " " +
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
           "AND " + PUBLIC_VISIBILITY + " " +
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
    Flux<PostCategoryProjection> findPostsWithCategory(List<Long> postIds);
    
    /**
     * 批量查询文章的标签信息
     * @param postIds 文章ID列表
     * @return 包含文章ID、标签ID和标签名称的结果
     */
    @Query("SELECT pt.post_id, pt.tag_id, t.name as tag_name " +
           "FROM t_post_tags pt " +
           "JOIN t_tags t ON pt.tag_id = t.id " +
           "WHERE pt.post_id IN (:postIds)")
    Flux<PostTagProjection> findPostsWithTags(List<Long> postIds);
    
    /**
     * 批量查询文章的评论数
     *
     * <p><b>只统计「已通过审核」的评论</b>（{@code status = 1}）——
     * 卡片上显示的评论数必须等于读者能看到的评论数。
     * 原先的写法只过滤 {@code is_deleted}，把<b>待审核和被拒绝的评论也算了进去</b>：
     * 明明只有 1 条评论可见，卡片却显示 3 条。
     *
     * <p>与 {@code CommentRepository#countByPostId} 的差别是<b>有意为之</b>：
     * 那个方法的返回值是「顶级评论分页列表」的 total（分页必须按顶层算），
     * 这里算的是文章卡片上的评论数（顶层 + 回复都算）。
     *
     * @param postIds 文章ID列表
     * @return 包含文章ID和评论数的结果
     */
    @Query("SELECT post_id, COUNT(*) as comment_count " +
           "FROM t_comments " +
           "WHERE post_id IN (:postIds) AND is_deleted = 0 AND status = 1 " +
           "GROUP BY post_id")
    Flux<Map<String, Object>> countCommentsByPostIds(List<Long> postIds);
    
    /**
     * 批量更新文章删除状态
     * @param ids 文章ID列表
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE t_posts SET is_deleted = 1, update_time = NOW() WHERE id IN (:ids) AND is_deleted = 0")
    Mono<Integer> batchSoftDelete(List<Long> ids);
} 