package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostStickyDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.entity.PostCategory;
import com.ryu.blog.service.ArticleService;
import com.ryu.blog.service.impl.article.ArticleCrudService;
import com.ryu.blog.service.impl.article.ArticlePublishCoordinator;
import com.ryu.blog.service.impl.article.ArticleQueryService;
import com.ryu.blog.service.impl.article.ArticleRelationService;
import com.ryu.blog.utils.OperatorContexts;
import com.ryu.blog.vo.MarkdownExportVO;
import com.ryu.blog.vo.PostAdminListVO;
import com.ryu.blog.vo.PostDetailVO;
import com.ryu.blog.vo.PostFrontListVO;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章服务实现类
 *
 * <p>本类只承载缓存注解（{@code @Cacheable} / {@code @CacheEvict}）、事务边界
 * 与参数转发，业务逻辑分散在四个专职组件里：
 *
 * <ul>
 *   <li>{@link ArticleCrudService} —— 创建、更新、状态变更、删除、导入导出</li>
 *   <li>{@link ArticleQueryService} —— 列表、详情、热门、相关推荐等读操作</li>
 *   <li>{@link ArticleRelationService} —— 文章与分类、标签的关联维护</li>
 *   <li>{@link ArticleViewAssembler} —— 列表 VO 装配与批量关联数据查询</li>
 * </ul>
 *
 * <p>缓存注解必须留在这一层：下沉到组件后，组件之间的自调用会绕过 Spring 代理，
 * 缓存会静默失效。
 *
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleCrudService crudService;
    private final ArticleQueryService queryService;
    private final ArticleRelationService relationService;
    private final ArticlePublishCoordinator publishCoordinator;

    // 注入自身代理以解决@Cacheable自调用问题
    private ArticleService self;

    /**
     * 设置自身代理，用于解决@Cacheable自调用问题
     * 使用@Lazy避免循环依赖
     */
    @Autowired
    public void setSelf(@Lazy ArticleService self) {
        this.self = self;
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    /**
     * 创建文章。
     *
     * <p><b>刻意不加 {@code @Transactional}</b> —— 文章写入本身的事务边界在
     * {@link ArticleCrudService#createArticle}；而「定时发布」还要往
     * {@code t_async_tasks} 写一条任务并访问 Redis，两者不该挤在同一条事务里：
     * 任务提交失败时文章必须已经落库（才能被退回草稿），
     * 混在一个事务里会变成「一起回滚」，退回草稿也就无从谈起。
     */
    @Override
    @CacheEvict(cacheNames = {
            CacheConstants.POST_FRONT_CACHE,
            CacheConstants.POST_HOT_CACHE
    }, allEntries = true)
    public Mono<Posts> createArticle(PostCreateDTO articleCreateDTO, Long userId) {
        return crudService.createArticle(articleCreateDTO, userId)
                .flatMap(article -> publishCoordinator.afterWrite(article,
                        articleCreateDTO.getPublishMode(),
                        articleCreateDTO.getScheduleTime(),
                        userId));
    }

    /**
     * 更新文章。
     *
     * <p><b>刻意不加 {@code @Transactional}</b> —— 理由同 {@link #createArticle}：
     * 事务边界在 {@link ArticleCrudService#updateArticle}，任务提交在事务之外。
     */
    @Override
    @Caching(evict = {
            // 详情缓存的 key 带用户后缀（见 getArticleDetailVO），相关推荐的 key 后缀是 limit，
            // 两者都无法用单个 key 精确失效，只能整块清。
            // 注意：Spring 的 @CacheEvict **不支持 '*' 通配符** —— 写成 "...:*" 只会去删一个
            // 字面量名叫 "...:*" 的 key，等于什么都没删。
            @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_CACHE, allEntries = true)
    })
    public Mono<Posts> updateArticle(PostUpdateDTO articleUpdateDTO) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return crudService.updateArticle(articleUpdateDTO, ctx)
                    .flatMap(article -> publishCoordinator.afterWrite(article,
                            articleUpdateDTO.getPublishMode(),
                            articleUpdateDTO.getScheduleTime(),
                            ctx.userId()));
        });
    }

    /**
     * 取消定时发布：文章退回草稿，对应的待执行任务一并撤销。
     *
     * <p>任务撤销是尽力而为 —— 就算没撤掉，任务到点时也会因为文章已经不是
     * 「待发布」而自行跳过。
     */
    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_CACHE, allEntries = true)
    })
    public Mono<Posts> cancelScheduledPublish(Long postId) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return crudService.cancelScheduledPublish(postId, ctx)
                    .flatMap(article -> publishCoordinator.cancelPublishTask(article)
                            .thenReturn(article));
        });
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_CACHE, allEntries = true)
    })
    public Mono<Posts> updateArticleStatus(PostStatusDTO statusDTO) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return crudService.updateArticleStatus(statusDTO, ctx);
        });
    }

    /**
     * 设置/取消置顶。
     *
     * <p>置顶会改变前台列表的排序，因此前台列表缓存必须一起清掉 ——
     * 否则列表页在缓存过期前看不到置顶效果。
     */
    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_CACHE, allEntries = true)
    })
    public Mono<Posts> updateSticky(PostStickyDTO stickyDTO) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return crudService.updateSticky(stickyDTO, ctx);
        });
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_CACHE, allEntries = true)
    })
    public Mono<Void> deleteArticle(Long id) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return crudService.deleteArticle(id, ctx);
        });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
            CacheConstants.POST_DETAIL_CACHE,
            CacheConstants.POST_FRONT_CACHE,
            CacheConstants.POST_HOT_CACHE,
            CacheConstants.POST_CACHE
    }, allEntries = true)
    public Mono<Void> batchDeleteArticles(List<String> ids) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return crudService.batchDeleteArticles(ids, ctx);
        });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
            CacheConstants.POST_FRONT_CACHE,
            CacheConstants.POST_HOT_CACHE
    }, allEntries = true)
    public Mono<Void> importMarkdownArticle(FilePart file, Long categoryId, Long userId) {
        return crudService.importMarkdownArticle(file, categoryId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<MarkdownExportVO> exportArticleToMarkdown(Long id) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            return crudService.exportArticleToMarkdown(id, ctx);
        });
    }

    // ------------------------------------------------------------------
    // 分类与标签关联
    // ------------------------------------------------------------------

    @Override
    public Mono<PostCategory> addArticleCategory(Long articleId, Long categoryId) {
        return relationService.addArticleCategory(articleId, categoryId);
    }

    @Override
    public Mono<Void> removeArticleCategory(Long articleId, Long categoryId) {
        return relationService.removeArticleCategory(articleId, categoryId);
    }

    @Override
    public Mono<Void> removeAllArticleCategories(Long articleId) {
        return relationService.removeAllArticleCategories(articleId);
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Long> getArticleCategoryIds(Long articleId) {
        return relationService.getArticleCategoryIds(articleId);
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Flux<Posts> getArticlesByCategoryId(Long categoryId, int page, int size) {
        return queryService.getArticlesByCategoryId(categoryId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Long> countArticlesByCategoryId(Long categoryId) {
        return queryService.countArticlesByCategoryId(categoryId);
    }

    @Override
    public Mono<Map<String, Object>> getFrontArticles(String cursor, int limit, String createTime, String direction) {
        // 使用self代理调用，确保@Cacheable生效
        return self.getFrontArticlesVO(cursor, limit, createTime, direction)
                .map(voList -> {
                    Map<String, Object> result = new HashMap<>();

                    // 判断是否有更多数据 - 因为getFrontArticlesVO可能返回limit+1条数据
                    boolean hasMore = voList.size() > limit;

                    // 取出实际要返回的文章列表
                    List<PostFrontListVO> resultList = hasMore ?
                            voList.subList(0, limit) : voList;

                    result.put("hasMore", hasMore);
                    result.put("records", resultList);

                    // 设置新的游标，用于下次查询
                    if (!resultList.isEmpty()) {
                        PostFrontListVO lastArticle = resultList.get(resultList.size() - 1);
                        result.put("cursor", lastArticle.getId().toString());
                        result.put("createTime", lastArticle.getCreateTime());
                    }

                    return result;
                })
                .doOnSuccess(r -> log.debug("前台游标分页查询文章成功: 返回记录数={}, 是否有更多={}",
                        ((List<?>) r.get("records")).size(), r.get("hasMore")))
                .doOnError(e -> log.error("前台游标分页查询文章失败: 错误信息={}", e.getMessage()));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_FRONT_CACHE, key = "'" + CacheConstants.POST_FRONT_KEY + "' + #cursor + ':' + #limit + ':' + #createTime + ':' + #direction", unless = "#result.isEmpty()")
    public Mono<List<PostFrontListVO>> getFrontArticlesVO(String cursor, int limit, String createTime, String direction) {
        return queryService.getFrontArticlesVO(cursor, limit, createTime, direction);
    }

    @Override
    public Flux<Posts> getRelatedArticles(Long postId, Integer limit) {
        return queryService.getRelatedArticles(postId, limit);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_CACHE, key = "'" + CacheConstants.POST_RELATED_KEY + "' + #postId + ':' + #limit", unless = "#result == null")
    public Flux<PostFrontListVO> getRelatedArticlesVO(Long postId, Integer limit) {
        return queryService.getRelatedArticlesVO(postId, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Posts> getPublishedArticles(int page, int size) {
        return queryService.getPublishedArticles(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Long> countPublishedArticles() {
        return queryService.countPublishedArticles();
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Posts> getArticlesByUserId(Long userId, int page, int size) {
        return queryService.getArticlesByUserId(userId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Long> countArticlesByUserId(Long userId) {
        return queryService.countArticlesByUserId(userId);
    }

    /**
     * 文章详情。
     *
     * <p><b>缓存 key 必须带上当前用户身份</b>：文章可见性由
     * {@code ArticleVisibilityPolicy.canView(article, currentUserId, canManageOthers)} 决定 ——
     * 未公开的文章只允许作者本人与「能管理他人」的管理者读。缓存命中时方法体
     * <b>根本不会执行</b>，可见性校验也就被跳过了 —— 如果 key 里只有文章 ID，
     * 作者缓存下来的草稿会被匿名访客直接读走。
     *
     * <p>key 的<b>最后两段</b>是「当前用户 ID」（匿名为 {@code null}）与
     * 「能否管理他人」。两项都参与可见性判定：只带用户 ID 的话，
     * 管理员读到的草稿详情会被缓存下来发给「同 ID 的普通用户」——
     * 后台能改别人的文章却读不到详情，两边也自相矛盾。
     *
     * <p>两项都由 {@code SaTokenUtils} 在组装响应式链时取得
     * （此时 Sa-Token 上下文还在；进了 flatMap 之后就可能取不到了）。
     */
    @Override
    @Cacheable(cacheNames = CacheConstants.POST_DETAIL_CACHE,
            key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #id + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentUserIdOrNull() + ':' + T(com.ryu.blog.utils.SaTokenUtils).currentCanManageOthers()",
            unless = "#result == null")
    public Mono<PostDetailVO> getArticleDetailVO(Long id) {
        return queryService.getArticleDetailVO(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>刻意不加 {@code @Transactional}</b> —— 这类只读分页方法必须让
     * {@link ArticleQueryService#getArticlePageVO} 内部的 {@code Mono.zip}
     * 两条富化查询各自拿到池连接。绑定事务会把整条链固定在同一条 R2DBC 连接上，
     * 两个线程并发向该连接的 {@code RequestQueue}（r2dbc-mysql 1.4.0 的
     * SPSC 队列，且 {@code submit()} 无锁）提交，会误判队满并抛
     * {@code Request queue is full}，随后被 {@code onErrorResume} 静默降级成空数据。
     *
     * <p>回归护栏：{@code ArticleQueryServiceTransactionBoundaryTest}。
     */
    @Override
    public Mono<PageResult<PostAdminListVO>> getArticlePageVO(int page, int size, String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime) {
        return queryService.getArticlePageVO(page, size, title, status, categoryId, tagId, startTime, endTime);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_HOT_CACHE, key = "'" + CacheConstants.POST_HOT_KEY + "' + #limit", unless = "#result == null")
    public Flux<Posts> getHotArticles(int limit) {
        return queryService.getHotArticles(limit);
    }

    // ------------------------------------------------------------------
    // 计数
    // ------------------------------------------------------------------
    //
    // 【已删除】原先这里的 incrementLikes / incrementComments / decrementComments
    // 已被整体移除，原因有二：
    //   1. 它们打的是 `UPDATE t_posts SET likes/comments = ...`，
    //      而 **t_posts 表里根本没有 likes 和 comments 两列**
    //      （schema.sql 只有 views），一调用就是
    //      `SQLSyntaxErrorException: Unknown column 'comments' in 'field list'`；
    //   2. 全文检索确认这两个方法**没有任何调用方**（连 Controller 都没有），
    //      属于「看起来有、其实从没用过」的死代码 —— 而死代码即风险。
    //
    // 文章评论数的**唯一来源**现在是派生查询
    // （PostsRepository#countCommentsByPostIds，从 t_comments GROUP BY post_id 统计），
    // 由 ArticleViewAssembler#batchGetArticleStats 装配进列表 VO。
    // 不再保留「冗余列 + 手工增减」的第二套口径。
}
