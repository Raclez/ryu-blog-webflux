package com.ryu.blog.service.impl.article;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.entity.Tag;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.PostMapper;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.TagRepository;
import com.ryu.blog.service.ContentService;
import com.ryu.blog.utils.SaTokenUtils;
import com.ryu.blog.vo.PostAdminListVO;
import com.ryu.blog.vo.PostDetailVO;
import com.ryu.blog.vo.PostFrontListVO;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.ai.model.ModelOptionsUtils.OBJECT_MAPPER;

/**
 * 文章查询服务
 *
 * <p>承载文章列表、详情、热门、相关推荐等读操作，以及列表 VO 的装配。
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleQueryService {

    private final PostsRepository postsRepository;
    private final TagRepository tagRepository;
    private final PostMapper postMapper;
    private final ContentService contentService;
    private final ArticleRelationService relationService;
    private final ArticleViewAssembler viewAssembler;

    /**
     * 根据分类分页查询文章
     */
    @Transactional(readOnly = true)
    public Flux<Posts> getArticlesByCategoryId(Long categoryId, int page, int size) {
        log.debug("根据分类获取文章列表: 分类ID={}, page={}, size={}", categoryId, page, size);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        return postsRepository.findByCategoryId(categoryId, pageRequest)
                .doOnComplete(() -> log.debug("根据分类获取文章列表完成: 分类ID={}", categoryId))
                .doOnError(e -> log.error("根据分类获取文章列表失败: 分类ID={}", categoryId, e));
    }

    /**
     * 统计分类下的文章数量
     */
    @Transactional(readOnly = true)
    public Mono<Long> countArticlesByCategoryId(Long categoryId) {
        log.debug("统计分类下的文章数量: 分类ID={}", categoryId);

        return postsRepository.countByCategoryId(categoryId)
                .doOnSuccess(count -> log.debug("分类下的文章数量: 分类ID={}, 数量={}", categoryId, count))
                .doOnError(e -> log.error("统计分类下的文章数量失败: 分类ID={}", categoryId, e));
    }

    /**
     * 获取已发布文章列表
     */
    @Transactional(readOnly = true)
    public Flux<Posts> getPublishedArticles(int page, int size) {
        log.debug("获取已发布文章列表: page={}, size={}", page, size);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        return postsRepository.findPublishedPosts(pageRequest)
                .doOnComplete(() -> log.debug("获取已发布文章列表完成: page={}, size={}", page, size))
                .doOnError(e -> log.error("获取已发布文章列表失败: page={}, size={}", page, size, e));
    }

    /**
     * 统计已发布文章数量
     */
    @Transactional(readOnly = true)
    public Mono<Long> countPublishedArticles() {
        log.debug("统计已发布文章数量");

        return postsRepository.countPublishedPosts()
                .doOnSuccess(count -> log.debug("已发布文章数量: {}", count))
                .doOnError(e -> log.error("统计已发布文章数量失败", e));
    }

    /**
     * 获取用户文章列表
     */
    @Transactional(readOnly = true)
    public Flux<Posts> getArticlesByUserId(Long userId, int page, int size) {
        log.debug("获取用户文章列表: 用户ID={}, page={}, size={}", userId, page, size);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        return postsRepository.findByUserId(userId, pageRequest)
                .doOnComplete(() -> log.debug("获取用户文章列表完成: 用户ID={}", userId))
                .doOnError(e -> log.error("获取用户文章列表失败: 用户ID={}", userId, e));
    }

    /**
     * 统计用户文章数量
     */
    @Transactional(readOnly = true)
    public Mono<Long> countArticlesByUserId(Long userId) {
        log.debug("统计用户文章数量: 用户ID={}", userId);

        return postsRepository.countByUserId(userId)
                .doOnSuccess(count -> log.debug("用户文章数量: 用户ID={}, 数量={}", userId, count))
                .doOnError(e -> log.error("统计用户文章数量失败: 用户ID={}", userId, e));
    }

    /**
     * 获取热门文章
     */
    public Flux<Posts> getHotArticles(int limit) {
        log.debug("获取热门文章: limit={}", limit);

        return postsRepository.findHotPosts(limit)
                .doOnComplete(() -> log.debug("获取热门文章完成: limit={}", limit))
                .doOnError(e -> log.error("获取热门文章失败: limit={}", limit, e));
    }

    /**
     * 获取相关文章
     */
    public Flux<Posts> getRelatedArticles(Long postId, Integer limit) {
        log.debug("获取相关文章: 文章ID={}, limit={}", postId, limit);

        if (postId == null) {
            log.warn("获取相关文章: 文章ID为空");
            return postsRepository.findPublishedPosts(PageRequest.of(0, limit));
        }

        // 首先获取当前文章的分类ID
        return relationService.getArticleCategoryIds(postId)
                .take(1) // 只取第一个分类
                .flatMap(categoryId -> {
                    log.debug("获取到文章分类: 文章ID={}, 分类ID={}", postId, categoryId);
                    // 根据分类查询相关文章
                    return postsRepository.findRelatedPostsByCategory(categoryId, postId, limit);
                })
                .switchIfEmpty(
                        // 如果没有分类或没有相关文章，则返回最新的文章
                        postsRepository.findPublishedPosts(PageRequest.of(0, limit))
                )
                .doOnComplete(() -> log.debug("获取相关文章完成: 文章ID={}", postId))
                .doOnError(e -> log.error("获取相关文章失败: 文章ID={}", postId, e));
    }

    /**
     * 获取相关博客推荐VO
     */
    public Flux<PostFrontListVO> getRelatedArticlesVO(Long postId, Integer limit) {
        log.info("获取相关博客推荐VO: 文章ID={}, 限制数量={}", postId, limit);

        return getRelatedArticles(postId, limit)
                .collectList()
                .flatMapMany(postsList -> {
                    if (postsList.isEmpty()) {
                        return Flux.empty();
                    }

                    Tuple2<List<Long>, Tuple2<List<Long>, List<Long>>> ids = viewAssembler.extractIdsFromPosts(postsList);
                    List<Long> postIds = ids.getT1();
                    List<Long> userIds = ids.getT2().getT1();
                    List<Long> coverImageIds = ids.getT2().getT2();

                    return viewAssembler.batchGetPostRelatedData(postIds, userIds, coverImageIds)
                            .flatMapMany(tuple -> {
                                Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                                Map<Long, List<String>> tagMap = tuple.getT2();
                                Map<Long, Tuple2<String, String>> userMap = tuple.getT3();
                                Map<Long, Tuple2<Integer, Integer>> statsMap = tuple.getT4();
                                Map<Long, String> coverMap = tuple.getT5();

                                return Flux.fromIterable(postsList)
                                        .map(post -> viewAssembler.buildPostFrontListVO(post, categoryMap, tagMap, userMap, statsMap, coverMap));
                            });
                });
    }

    /**
     * 前台游标分页查询文章VO
     */
    public Mono<List<PostFrontListVO>> getFrontArticlesVO(String cursor, int limit, String createTime, String direction) {
        log.debug("前台游标分页查询文章VO: cursor={}, limit={}, createTime={}, direction={}", cursor, limit, createTime, direction);

        String cursorParam = StringUtils.hasText(cursor) ? cursor : null;
        String createTimeParam = StringUtils.hasText(createTime) ? createTime : null;
        String directionParam = validateDirection(direction);

        return postsRepository.findFrontPosts(cursorParam, limit + 1, createTimeParam, directionParam)
                .collectList()
                .flatMap(articles -> buildFrontArticlesVOList(articles, limit))
                .doOnError(e -> log.error("前台游标分页查询文章VO失败", e));
    }

    /**
     * 分页查询管理端文章列表VO
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：方法内部的 {@code buildAdminListVOs}
     * 会用 {@code Mono.zip} 并发执行「分类」和「用户」两条富化查询。
     * 一旦绑定事务，R2DBC 会把整条链固定在同一条连接上，两条查询便同时向
     * 该连接的 {@code RequestQueue} 提交 —— 而 r2dbc-mysql 1.4.0 的请求队列是
     * SPSC 队列且在 {@code submit()} 时无锁，并发 offer 会误判队满并抛
     * {@code IllegalStateException: Request queue is full}（2026-10-28 事故）。
     *
     * <p>本方法全是读操作，事务并不提供实际收益：count 与列表页之间的一致快照
     * 对分页列表本就无意义。去掉事务后每条查询各自取池连接，竞态不复存在，
     * 同时两条富化查询得以真正并行。前台路径 {@code getFrontArticlesVO} 一直是
     * 同样的写法（无事务 + 5 路 zip），从未出现该问题。
     *
     * <p>若将来确实需要事务语义，必须把 {@code buildAdminListVOs} 里的
     * {@code Mono.zip} 改成串行链（{@code flatMap}），否则竞态会立刻回归。
     * 见 {@code ArticleQueryServiceTransactionBoundaryTest}。
     */
    public Mono<PageResult<PostAdminListVO>> getArticlePageVO(int page, int size, String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime) {
        // 不把标题（用户创作内容）写进日志，只记录查询条件本身
        log.debug("分页查询文章VO: page={}, size={}, hasTitle={}, status={}, categoryId={}, tagId={}, startTime={}, endTime={}",
                page, size, StringUtils.hasText(title), status, categoryId, tagId, startTime, endTime);

        String titleParam = StringUtils.hasText(title) ? title : null;
        String startTimeParam = StringUtils.hasText(startTime) ? startTime : null;
        String endTimeParam = StringUtils.hasText(endTime) ? endTime : null;

        return postsRepository.countPostsByCondition(titleParam, status, categoryId, tagId, startTimeParam, endTimeParam)
                .flatMap(total -> buildPageResult(page, size, total, titleParam, status, categoryId, tagId, startTimeParam, endTimeParam))
                .doOnError(e -> log.error("分页查询文章VO失败: page={}, size={}, hasTitle={}",
                        page, size, StringUtils.hasText(title), e));
    }

    /**
     * 获取文章详情VO
     */
    public Mono<PostDetailVO> getArticleDetailVO(Long id) {
        log.info("获取文章详情VO: ID={}", id);

        // 在订阅前取当前登录用户：Sa-Token 的上下文靠 SaReactorSyncHolder 随响应式链传播，
        // 进了 flatMap 之后再取有可能已经拿不到。取不到就当匿名处理。
        Long currentUserId = currentUserIdOrNull();
        // 可见性还有「管理员」一档，可见性判定依赖两项身份信息（见 ArticleVisibilityPolicy）。
        boolean canManageOthers = canManageOthersOrFalse();

        return getArticleDetailVOFromDB(id, currentUserId, canManageOthers)
                .doOnSuccess(detailVO -> log.debug("获取文章详情VO成功: ID={}", id))
                .doOnError(e -> log.error("获取文章详情VO失败: ID={}", id, e));
    }

    /**
     * 取当前登录用户ID，未登录或上下文不可用时返回 null
     *
     * <p><b>必须与缓存 key 用同一个来源</b>：详情缓存的 key 里带着当前用户 ID
     * （见 {@code ArticleServiceImpl#getArticleDetailVO}），如果这里取到的身份
     * 与缓存 key 里算出的身份不是同一个（比如一个用 {@code StpUtil}、另一个用别的
     * 途径），就会出现「A 的可见性判定配 B 的缓存」的错配。统一走
     * {@link SaTokenUtils#currentUserIdOrNull()}。
     */
    private Long currentUserIdOrNull() {
        return SaTokenUtils.currentUserIdOrNull();
    }

    /**
     * 当前操作者是否拥有「管理他人资源」的能力
     *
     * <p>与 {@link #currentUserIdOrNull()} 同源同时机：可见性判定依赖这两项，
     * 缓存 key 也依赖这两项 —— 取值来源必须一致，否则会出现
     * 「A 的可见性判定配 B 的缓存 key」。
     */
    private boolean canManageOthersOrFalse() {
        return SaTokenUtils.currentCanManageOthers();
    }

    /**
     * 构建分页结果
     */
    private Mono<PageResult<PostAdminListVO>> buildPageResult(int page, int size, Long total,
                                                                String titleParam, Integer status, Long categoryId, Long tagId,
                                                                String startTimeParam, String endTimeParam) {
        PageResult<PostAdminListVO> pageResult = new PageResult<>();
        pageResult.setTotal(total);
        pageResult.setCurrent(page + 1);
        pageResult.setSize(size);
        pageResult.setPages((total + size - 1) / size);

        if (total == 0) {
            pageResult.setRecords(Collections.emptyList());
            return Mono.just(pageResult);
        }

        return postsRepository.findPostsByCondition(titleParam, status, categoryId, tagId, startTimeParam, endTimeParam, page * size, size)
                .collectList()
                .flatMap(postsList -> buildAdminListVOs(postsList, pageResult));
    }

    /**
     * 构建管理端列表VO
     *
     * <p><b>这里的 {@code Mono.zip} 要求调用方处于事务之外</b>，理由见
     * {@link #getArticlePageVO} 的 Javadoc：两条分支必须各自拿到池连接。
     * 若要在事务内复用本方法，请把 zip 换成串行链。
     */
    private Mono<PageResult<PostAdminListVO>> buildAdminListVOs(List<Posts> postsList, PageResult<PostAdminListVO> pageResult) {
        if (postsList.isEmpty()) {
            pageResult.setRecords(Collections.emptyList());
            return Mono.just(pageResult);
        }

        List<Long> postIds = postsList.stream().map(Posts::getId).collect(Collectors.toList());
        List<Long> userIds = postsList.stream().map(Posts::getUserId).distinct().collect(Collectors.toList());

        Mono<Map<Long, Tuple2<Long, String>>> categoriesMonoMap =
                viewAssembler.batchGetArticleCategories(postIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, Tuple2<String, String>>> usersMonoMap =
                viewAssembler.batchGetUserInfo(userIds).subscribeOn(Schedulers.parallel());

        return Mono.zip(categoriesMonoMap, usersMonoMap)
                .map(tuple -> {
                    Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                    Map<Long, Tuple2<String, String>> userMap = tuple.getT2();

                    List<PostAdminListVO> voList = postsList.stream()
                            .map(post -> viewAssembler.buildPostAdminListVO(post, categoryMap, userMap))
                            .collect(Collectors.toList());

                    pageResult.setRecords(voList);
                    return pageResult;
                });
    }

    /**
     * 构建前台文章VO列表
     */
    private Mono<List<PostFrontListVO>> buildFrontArticlesVOList(List<Posts> articles, int limit) {
        List<Posts> resultList = articles.size() > limit ? articles.subList(0, limit) : articles;

        if (resultList.isEmpty()) {
            return Mono.just(List.of());
        }

        Tuple2<List<Long>, Tuple2<List<Long>, List<Long>>> ids = viewAssembler.extractIdsFromPosts(resultList);
        List<Long> postIds = ids.getT1();
        List<Long> userIds = ids.getT2().getT1();
        List<Long> coverImageIds = ids.getT2().getT2();

        return viewAssembler.batchGetPostRelatedData(postIds, userIds, coverImageIds)
                .map(tuple -> {
                    Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                    Map<Long, List<String>> tagMap = tuple.getT2();
                    Map<Long, Tuple2<String, String>> userMap = tuple.getT3();
                    Map<Long, Tuple2<Integer, Integer>> statsMap = tuple.getT4();
                    Map<Long, String> coverMap = tuple.getT5();

                    return resultList.stream()
                            .map(post -> viewAssembler.buildPostFrontListVO(post, categoryMap, tagMap, userMap, statsMap, coverMap))
                            .collect(Collectors.toList());
                });
    }

    /**
     * 验证方向参数
     */
    private String validateDirection(String direction) {
        String directionParam = StringUtils.hasText(direction) ? direction : "older";
        if (!directionParam.equals("newer") && !directionParam.equals("older") && !directionParam.equals("comprehensive")) {
            log.warn("方向参数错误: {}, 将使用默认值 'older'", direction);
            return "older";
        }
        return directionParam;
    }

    /**
     * 从数据库获取文章详情VO
     *
     * @param id 文章ID
     * @param currentUserId 当前登录用户ID（未登录为 null）
     * @param canManageOthers 当前操作者是否拥有「管理他人资源」的能力
     * @return 文章详情VO
     */
    private Mono<PostDetailVO> getArticleDetailVOFromDB(Long id, Long currentUserId, boolean canManageOthers) {
        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(article -> {
                    // 检查文章是否已删除
                    if (Boolean.TRUE.equals(article.getIsDeleted())) {
                        return Mono.error(BusinessException.postAlreadyDeleted());
                    }

                    // 可见性检查：只有「已发布且公开」的文章才能被匿名读到，
                    // 其余只允许作者本人与「能管理他人」的管理者（规则见 ArticleVisibilityPolicy）。
                    // 对匿名访问者一律按「文章不存在」处理，不泄露文章是否存在。
                    if (!ArticleVisibilityPolicy.canView(article, currentUserId, canManageOthers)) {
                        log.debug("文章未公开，拒绝详情访问: ID={}, status={}, visibility={}",
                                id, article.getStatus(), article.getVisibility());
                        return Mono.error(BusinessException.postNotFound());
                    }

                    // 使用MapStruct转换为VO
                    PostDetailVO detailVO = postMapper.toDetailVO(article);

                    // 获取文章分类ID
                    Mono<List<Long>> categoryIdsMono = relationService.getArticleCategoryIds(article.getId())
                            .collectList();

                    // 获取文章标签ID
                    Mono<List<Long>> tagIdsMono = tagRepository.findByPostId(article.getId())
                            .map(Tag::getId)
                            .collectList();

                    // 获取封面图片URL
                    Mono<String> coverImageUrlMono = viewAssembler.getImageUrl(article.getCoverImageId());

                    // 计算阅读时间
                    Mono<Integer> readingTimeMono = contentService.calculateReadingTime(article.getContent());

                    // 解析SEO信息
                    String seoTitle = "";
                    String seoDescription = "";
                    String slug = "";
                    // 如果有SEO元数据，进行解析
                    if (article.getSeoMeta() != null && !article.getSeoMeta().isEmpty()) {
                        try {
                            Map<String, String> seoMeta = OBJECT_MAPPER.readValue(article.getSeoMeta(), Map.class);
                            seoTitle = seoMeta.getOrDefault("seoTitle", "");
                            seoDescription = seoMeta.getOrDefault("seoDescription", "");
                            slug = seoMeta.getOrDefault("slug", "");
                            // 不把 SEO 标题/描述/别名（用户创作内容）写进日志
                            log.debug("文章[{}]解析到SEO元数据: hasTitle={}, hasDescription={}, hasSlug={}",
                                    article.getId(),
                                    !seoTitle.isEmpty(), !seoDescription.isEmpty(), !slug.isEmpty());
                        } catch (JsonProcessingException e) {
                            log.error("解析SEO元数据失败", e);
                        }
                    }

                    // 设置额外属性
                    final String finalSeoTitle = seoTitle;
                    final String finalSeoDescription = seoDescription;
                    final String finalSlug = slug;

                    // 并行获取所有关联数据
                    return Mono.zip(categoryIdsMono, tagIdsMono, coverImageUrlMono, readingTimeMono)
                            .map(tuple -> {
                                List<Long> categoryIds = tuple.getT1();
                                List<Long> tagIds = tuple.getT2();
                                String coverImageUrl = tuple.getT3();
                                Integer readingTime = tuple.getT4();

                                // 设置分类ID（如果有）
                                if (!categoryIds.isEmpty()) {
                                    detailVO.setCategoryId(categoryIds.get(0));
                                }

                                // 设置标签IDs
                                detailVO.setTagsIds(tagIds);

                                // 设置封面图片URL
                                detailVO.setCoverImageUrl(coverImageUrl);

                                // 设置阅读时间
                                detailVO.setReadingTime(readingTime);

                                // 设置SEO信息
                                detailVO.setSeoTitle(finalSeoTitle);
                                detailVO.setSeoDescription(finalSeoDescription);
                                // slug 现在是 t_posts 的真实列，由 mapper 自动映射；
                                // seo_meta 里的旧值只在它非空时兜底，
                                // 否则会把实体里真实的 slug 覆盖成空字符串
                                if (finalSlug != null && !finalSlug.isEmpty()) {
                                    detailVO.setSlug(finalSlug);
                                }

                                // 不把标题（用户创作内容）写进日志
                                log.debug("文章详情VO构建完成: ID={}, 分类ID={}, 标签数={}",
                                        detailVO.getId(), detailVO.getCategoryId(),
                                        detailVO.getTagsIds() != null ? detailVO.getTagsIds().size() : 0);

                                return detailVO;
                            });
                });
    }
}
