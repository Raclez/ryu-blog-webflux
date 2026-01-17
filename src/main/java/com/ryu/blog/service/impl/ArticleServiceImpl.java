package com.ryu.blog.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.constant.SystemConstants;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.*;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.PostMapper;
import com.ryu.blog.repository.*;
import com.ryu.blog.service.*;
import com.ryu.blog.utils.MarkdownUtils;
import com.ryu.blog.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 文章服务实现类
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final PostsRepository postsRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final ArticleVersionService articleVersionService;
    private final PostMapper postMapper;
    private final DatabaseClient databaseClient;
    private final ContentService contentService;
    private final FileService fileService;
    private final ViewHistoryService viewHistoryService;
    
    // 注入自身代理以解决@Cacheable自调用问题
    private ArticleService self;

    // 常量定义
    private static final int BATCH_SIZE = 50;
    private static final int DEFAULT_EXCERPT_LENGTH = 200;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final String DEFAULT_COVER_IMAGE = "/assets/images/default-cover.jpg";
    private static final String DEFAULT_AVATAR = "/assets/images/default-avatar.png";
    private static final String UNKNOWN_USER = "未知用户";
    private static final String UNNAMED_ARTICLE = "未命名文章";
    
    // 共享的ObjectMapper实例，避免重复创建
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 设置自身代理，用于解决@Cacheable自调用问题
     * 使用@Lazy避免循环依赖
     */
    @Autowired
    public void setSelf(@Lazy ArticleService self) {
        this.self = self;
    }

    /**
     * 处理SEO元数据并序列化为JSON字符串
     *
     * @param seoTitle       SEO标题
     * @param seoDescription SEO描述
     * @return 序列化后的JSON字符串，失败时返回null
     */
    private String processSeoMetadata(String seoTitle, String seoDescription) {
        if (seoTitle == null && seoDescription == null) {
            return null;
        }

        Map<String, String> seoMeta = new HashMap<>();
        if (seoTitle != null) {
            seoMeta.put("seoTitle", seoTitle);
        }
        if (seoDescription != null) {
            seoMeta.put("seoDescription", seoDescription);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(seoMeta);
        } catch (JsonProcessingException e) {
            log.error("SEO元数据序列化失败", e);
            return null;
        }
    }

    /**
     * 添加文章标签关联
     *
     * @param articleId 文章ID
     * @param tagIds    标签ID列表
     * @return 完成信号
     */
    private Mono<Void> addArticleTags(Long articleId, List<String> tagIds) {
        if (articleId == null || tagIds == null || tagIds.isEmpty()) {
            return Mono.empty();
        }

        log.debug("添加文章标签关联: 文章ID={}, 标签IDs={}", articleId, tagIds);

        // 先删除现有关联
        return postTagRepository.deleteByPostId(articleId)
                .then(Flux.fromIterable(tagIds)
                        .<PostTag>flatMap(tagIdStr -> {
                            try {
                                Long tagId = Long.parseLong(tagIdStr);
                                PostTag postTag = new PostTag();
                                postTag.setPostId(articleId);
                                postTag.setTagId(tagId);
                                postTag.setCreateTime(LocalDateTime.now());
                                return postTagRepository.save(postTag);
                            } catch (NumberFormatException e) {
                                log.warn("标签ID格式错误: {}, 将被忽略", tagIdStr);
                                return Mono.empty();
                            }
                        })
                        .collectList()
                        .doOnSuccess(savedTags -> log.debug("文章标签关联保存成功: 文章ID={}, 保存数量={}", articleId, savedTags.size()))
                        .doOnError(e -> log.error("文章标签关联保存失败: 文章ID={}, 错误={}", articleId, e.getMessage()))
                        .then());
    }

    /**
     * 移除文章所有标签关联
     *
     * @param articleId 文章ID
     * @return 完成信号
     */
    private Mono<Void> removeAllArticleTags(Long articleId) {
        log.debug("移除文章所有标签关联: 文章ID={}", articleId);

        return postTagRepository.deleteByPostId(articleId)
                .doOnSuccess(count -> log.debug("移除文章所有标签关联成功: 文章ID={}, 删除数量={}", articleId, count))
                .doOnError(e -> log.error("移除文章所有标签关联失败: 文章ID={}, 错误={}", articleId, e.getMessage()))
                .then();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
            CacheConstants.POST_FRONT_CACHE_NAME,
            CacheConstants.POST_HOT_CACHE_NAME
    }, allEntries = true)
    public Mono<Posts> createArticle(PostCreateDTO articleCreateDTO, Long userId) {
        log.info("创建文章: 标题={}", articleCreateDTO.getTitle());

        Posts article = new Posts();
        article.setTitle(articleCreateDTO.getTitle());
        article.setContent(articleCreateDTO.getContent());
        article.setExcerpt(articleCreateDTO.getExcerpt());
        article.setCoverImageId(articleCreateDTO.getCoverImageId());
        article.setStatus(Posts.Status.DRAFT);
        article.setIsOriginal(articleCreateDTO.getIsOriginal() != null ? articleCreateDTO.getIsOriginal() : SystemConstants.YES);


        article.setSort(articleCreateDTO.getSort() != null ? articleCreateDTO.getSort() : 0);
        article.setAllowComment(articleCreateDTO.getAllowComment() != null ? articleCreateDTO.getAllowComment() : SystemConstants.YES);
        article.setSourceUrl(articleCreateDTO.getSourceUrl());
        article.setLicense(articleCreateDTO.getLicense());
        article.setViews(0);

        article.setUserId(userId != null ? userId : 1L); // 使用传入的用户ID或默认用户ID
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());

        // 如果状态是已发布，设置发布时间
        if (article.getStatus() == Posts.Status.PUBLISHED) {
            article.setPublishTime(LocalDateTime.now());
        }

        article.setVisibility(articleCreateDTO.getVisibility() != null ? articleCreateDTO.getVisibility() : Posts.Visibility.PUBLIC);
        article.setPassword(articleCreateDTO.getPassword());
        article.setIsDeleted(SystemConstants.NOT_DELETED);

        // 处理SEO元数据
        String seoMeta = processSeoMetadata(articleCreateDTO.getSeoTitle(), articleCreateDTO.getSeoDescription());
        article.setSeoMeta(seoMeta);

        // 处理摘要
        Mono<String> excerptMono = StringUtils.hasText(articleCreateDTO.getExcerpt())
                ? Mono.just(articleCreateDTO.getExcerpt())
                : contentService.generateExcerpt(articleCreateDTO.getContent(), DEFAULT_EXCERPT_LENGTH);

        // 保存文章及关联数据
        return excerptMono
                .doOnNext(article::setExcerpt)
                .then(postsRepository.save(article))
                .flatMap(savedArticle -> {
                    // 创建一个Mono列表，用于保存所有操作
                    List<Mono<?>> operations = new ArrayList<>();

                    // 保存文章分类关联
                    if (articleCreateDTO.getCategoryId() != null) {
                        log.debug("关联文章分类: 文章ID: {}, 分类ID: {}", savedArticle.getId(), articleCreateDTO.getCategoryId());
                        operations.add(addArticleCategory(savedArticle.getId(), articleCreateDTO.getCategoryId()));
                    }

                    // 保存文章标签关联
                    if (articleCreateDTO.getTagsIds() != null && !articleCreateDTO.getTagsIds().isEmpty()) {
                        log.debug("关联文章标签: 文章ID: {}, 标签IDs: {}", savedArticle.getId(), articleCreateDTO.getTagsIds());
                        operations.add(addArticleTags(savedArticle.getId(), articleCreateDTO.getTagsIds()));
                    }

                    // 创建文章版本
                    log.debug("创建文章初始版本: 文章ID: {}", savedArticle.getId());
                    operations.add(articleVersionService.createVersion(savedArticle, "初始版本"));

                    // 并行执行所有操作
                    return Mono.when(operations)
                            .thenReturn(savedArticle);
                })
                .doOnSuccess(savedArticle -> log.info("文章创建成功: ID={}, 标题={}", savedArticle.getId(), savedArticle.getTitle()))
                .doOnError(e -> log.error("文章创建失败: 标题={}, 错误信息={}", articleCreateDTO.getTitle(), e.getMessage()));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE_NAME, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #articleUpdateDTO.id"),
            @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE_NAME, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE_NAME, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_CACHE_NAME, key = "'" + CacheConstants.POST_RELATED_KEY + "' + #articleUpdateDTO.id + ':*'")
    })
    public Mono<Posts> updateArticle(PostUpdateDTO articleUpdateDTO) {
        log.info("根据DTO更新文章: ID={}", articleUpdateDTO.getId());

        return postsRepository.findById(articleUpdateDTO.getId())
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(existingArticle -> {
                    // 保存旧内容作为比较
                    String oldContent = existingArticle.getContent();

                    // 使用MapStruct更新实体
                    postMapper.updateEntityFromDTO(articleUpdateDTO, existingArticle);

                    // 设置更新时间
                    existingArticle.setUpdateTime(LocalDateTime.now());


                    // 更新SEO信息
                    String seoMeta = processSeoMetadata(articleUpdateDTO.getSeoTitle(), articleUpdateDTO.getSeoDescription());
                    if (seoMeta != null) {
                        existingArticle.setSeoMeta(seoMeta);
                    }

                    // 如果文章状态是已发布，设置发布时间
                    if (articleUpdateDTO.getStatus() != null &&
                            articleUpdateDTO.getStatus() == Posts.Status.PUBLISHED &&
                            existingArticle.getPublishTime() == null) {
                        existingArticle.setPublishTime(LocalDateTime.now());
                    }

                    // 保存更新后的文章
                    return postsRepository.save(existingArticle)
                            .flatMap(updatedArticle -> {
                                // 创建一个Mono列表，用于保存所有操作
                                List<Mono<?>> operations = new ArrayList<>();

                                // 处理分类关联
                                if (articleUpdateDTO.getCategoryId() != null) {
                                    operations.add(
                                            removeAllArticleCategories(updatedArticle.getId())
                                                    .then(addArticleCategory(updatedArticle.getId(), articleUpdateDTO.getCategoryId()))
                                    );
                                }

                                // 处理标签关联
                                if (articleUpdateDTO.getTagsIds() != null) {
                                    operations.add(
                                            addArticleTags(updatedArticle.getId(), articleUpdateDTO.getTagsIds())
                                    );
                                }

                                // 如果内容发生变更，创建新版本
                                if (!oldContent.equals(updatedArticle.getContent())) {
                                    log.debug("创建文章新版本: 文章ID: {}", updatedArticle.getId());
                                    operations.add(articleVersionService.createVersion(updatedArticle, "内容更新"));
                                }

                                // 并行执行所有操作
                                return Mono.when(operations)
                                        .thenReturn(updatedArticle);
                            });
                })
                .doOnSuccess(updatedArticle -> log.info("文章更新成功: ID={}, 标题={}", updatedArticle.getId(), updatedArticle.getTitle()))
                .doOnError(e -> log.error("文章更新失败: ID={}, 错误信息={}", articleUpdateDTO.getId(), e.getMessage()));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE_NAME, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #statusDTO.id"),
            @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE_NAME, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE_NAME, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_CACHE_NAME, key = "'" + CacheConstants.POST_RELATED_KEY + "' + #statusDTO.id + ':*'")
    })
    public Mono<Posts> updateArticleStatus(PostStatusDTO statusDTO) {
        log.info("更新文章状态: ID={}, 状态={}", statusDTO.getId(), statusDTO.getStatus());

        return postsRepository.findById(statusDTO.getId())
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(existingArticle -> {
                    // 使用MapStruct更新实体状态
                    postMapper.updateStatusFromDTO(statusDTO, existingArticle);

                    // 设置更新时间
                    existingArticle.setUpdateTime(LocalDateTime.now());

                    // 如果从非发布状态变为已发布状态，设置发布时间
                    if (existingArticle.getStatus() == Posts.Status.PUBLISHED && existingArticle.getPublishTime() == null) {
                        existingArticle.setPublishTime(LocalDateTime.now());
                    }

                    return postsRepository.save(existingArticle);
                })
                .doOnSuccess(updatedArticle -> log.info("文章状态更新成功: ID={}, 状态={}", updatedArticle.getId(), updatedArticle.getStatus()))
                .doOnError(e -> log.error("文章状态更新失败: ID={}, 错误信息={}", statusDTO.getId(), e.getMessage()));
    }


    @Override
    @Transactional(readOnly = true)
    public Flux<Posts> getArticlesByCategoryId(Long categoryId, int page, int size) {
        log.debug("根据分类获取文章列表: 分类ID={}, page={}, size={}", categoryId, page, size);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        return postsRepository.findByCategoryId(categoryId, pageRequest)
                .doOnComplete(() -> log.debug("根据分类获取文章列表完成: 分类ID={}", categoryId))
                .doOnError(e -> log.error("根据分类获取文章列表失败: 分类ID={}, 错误信息={}", categoryId, e.getMessage()));
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Long> countArticlesByCategoryId(Long categoryId) {
        log.debug("统计分类下的文章数量: 分类ID={}", categoryId);

        return postsRepository.countByCategoryId(categoryId)
                .doOnSuccess(count -> log.debug("分类下的文章数量: 分类ID={}, 数量={}", categoryId, count))
                .doOnError(e -> log.error("统计分类下的文章数量失败: 分类ID={}, 错误信息={}", categoryId, e.getMessage()));
    }

    @Override
    public Mono<PostCategory> addArticleCategory(Long articleId, Long categoryId) {
        log.debug("添加文章分类关联: 文章ID={}, 分类ID={}", articleId, categoryId);

        return postCategoryRepository.findByPostIdAndCategoryId(articleId, categoryId)
                .switchIfEmpty(Mono.defer((Supplier<Mono<PostCategory>>) () -> {
                    PostCategory articleCategory = new PostCategory();
                    articleCategory.setPostId(articleId);
                    articleCategory.setCategoryId(categoryId);
                    articleCategory.setCreateTime(LocalDateTime.now());
                    return postCategoryRepository.save(articleCategory);
                }))
                .doOnSuccess(ac -> log.debug("添加文章分类关联成功: 文章ID={}, 分类ID={}", articleId, categoryId))
                .doOnError(e -> log.error("添加文章分类关联失败: 文章ID={}, 分类ID={}, 错误信息={}", articleId, categoryId, e.getMessage()));
    }

    @Override
    public Mono<Void> removeArticleCategory(Long articleId, Long categoryId) {
        log.debug("移除文章分类关联: 文章ID={}, 分类ID={}", articleId, categoryId);

        return postCategoryRepository.findByPostIdAndCategoryId(articleId, categoryId)
                .flatMap(postCategoryRepository::delete)
                .doOnSuccess(v -> log.debug("移除文章分类关联成功: 文章ID={}, 分类ID={}", articleId, categoryId))
                .doOnError(e -> log.error("移除文章分类关联失败: 文章ID={}, 分类ID={}, 错误信息={}", articleId, categoryId, e.getMessage()));
    }

    @Override
    public Mono<Void> removeAllArticleCategories(Long articleId) {
        log.debug("移除文章所有分类关联: 文章ID={}", articleId);

        return postCategoryRepository.deleteByPostId(articleId)
                .doOnSuccess(v -> log.debug("移除文章所有分类关联成功: 文章ID={}", articleId))
                .doOnError(e -> log.error("移除文章所有分类关联失败: 文章ID={}, 错误信息={}", articleId, e.getMessage()));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Long> getArticleCategoryIds(Long articleId) {
        log.debug("获取文章的分类IDs: 文章ID={}", articleId);

        return postCategoryRepository.findByPostId(articleId)
                .map(PostCategory::getCategoryId)
                .doOnComplete(() -> log.debug("获取文章的分类IDs完成: 文章ID={}", articleId))
                .doOnError(e -> log.error("获取文章的分类IDs失败: 文章ID={}, 错误信息={}", articleId, e.getMessage()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
            CacheConstants.POST_DETAIL_CACHE_NAME,
            CacheConstants.POST_FRONT_CACHE_NAME,
            CacheConstants.POST_HOT_CACHE_NAME,
            CacheConstants.POST_CACHE_NAME
    }, allEntries = true)
    public Mono<Void> batchDeleteArticles(List<String> ids) {
        log.info("批量删除文章: IDs={}", ids);

        if (ids == null || ids.isEmpty()) {
            log.warn("批量删除文章: 传入的ID列表为空");
            return Mono.empty();
        }

        // 将字符串ID转换为Long类型
        List<Long> longIds;
        try {
            longIds = ids.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            log.error("批量删除文章: ID格式错误: {}", e.getMessage());
            return Mono.error(new BusinessException("ID格式错误: " + e.getMessage()));
        }

        log.debug("开始批量删除文章: 数量={}", longIds.size());

        // 分批处理，避免一次处理过多数据
        List<List<Long>> batches = new ArrayList<>();
        for (int i = 0; i < longIds.size(); i += BATCH_SIZE) {
            batches.add(longIds.subList(i, Math.min(i + BATCH_SIZE, longIds.size())));
        }

        // 逐批处理 - 使用自定义Repository批量删除方法
        return Flux.fromIterable(batches)
                .flatMap(batchIds -> 
                    // 使用批量软删除方法
                    postsRepository.batchSoftDelete(batchIds)
                        .flatMap(deletedCount -> {
                            log.debug("批量删除结果: 删除数量={}", deletedCount);
                            // 批量删除文章分类关联
                            return Flux.fromIterable(batchIds)
                                .flatMap(postCategoryRepository::deleteByPostId)
                                .then(Mono.just(deletedCount));
                        })
                )
                .then()
                .doOnSuccess(v -> log.info("批量删除文章完成: 请求删除数量={}", longIds.size()))
                .doOnError(e -> {
                    if (e instanceof BusinessException) {
                        log.warn("批量删除文章失败: 业务异常: {}", e.getMessage());
                    } else {
                        log.error("批量删除文章失败: 错误信息={}", e.getMessage());
                    }
                });
    }


    @Override
    public Mono<Map<String, Object>> getFrontArticles(String cursor, int limit, String createTime, String direction) {
        log.debug("前台游标分页查询文章: cursor={}, limit={}, createTime={}, direction={}",
                cursor, limit, createTime, direction);

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
    public Flux<Posts> getRelatedArticles(Long postId, Integer limit) {
        log.debug("获取相关文章: 文章ID={}, limit={}", postId, limit);

        if (postId == null) {
            log.warn("获取相关文章: 文章ID为空");
            return postsRepository.findPublishedPosts(PageRequest.of(0, limit));
        }

        // 首先获取当前文章的分类ID
        return getArticleCategoryIds(postId)
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
                .doOnError(e -> log.error("获取相关文章失败: 文章ID={}, 错误信息={}", postId, e.getMessage()));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_CACHE_NAME, key = "'" + CacheConstants.POST_RELATED_KEY + "' + #postId + ':' + #limit", unless = "#result == null")
    public Flux<PostFrontListVO> getRelatedArticlesVO(Long postId, Integer limit) {
        log.info("获取相关博客推荐VO: 文章ID={}, 限制数量={}", postId, limit);

        return getRelatedArticles(postId, limit)
                .collectList()
                .flatMapMany(postsList -> {
                    if (postsList.isEmpty()) {
                        return Flux.empty();
                    }

                    Tuple2<List<Long>, Tuple2<List<Long>, List<Long>>> ids = extractIdsFromPosts(postsList);
                    List<Long> postIds = ids.getT1();
                    List<Long> userIds = ids.getT2().getT1();
                    List<Long> coverImageIds = ids.getT2().getT2();

                    return batchGetPostRelatedData(postIds, userIds, coverImageIds)
                            .flatMapMany(tuple -> {
                                Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                                Map<Long, List<String>> tagMap = tuple.getT2();
                                Map<Long, Tuple2<String, String>> userMap = tuple.getT3();
                                Map<Long, Tuple2<Integer, Integer>> statsMap = tuple.getT4();
                                Map<Long, String> coverMap = tuple.getT5();

                                return Flux.fromIterable(postsList)
                                        .map(post -> buildPostFrontListVO(post, categoryMap, tagMap, userMap, statsMap, coverMap));
                            });
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Posts> getPublishedArticles(int page, int size) {
        log.debug("获取已发布文章列表: page={}, size={}", page, size);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        return postsRepository.findPublishedPosts(pageRequest)
                .doOnComplete(() -> log.debug("获取已发布文章列表完成: page={}, size={}", page, size))
                .doOnError(e -> log.error("获取已发布文章列表失败: page={}, size={}, 错误信息={}", page, size, e.getMessage()));
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Long> countPublishedArticles() {
        log.debug("统计已发布文章数量");

        return postsRepository.countPublishedPosts()
                .doOnSuccess(count -> log.debug("已发布文章数量: {}", count))
                .doOnError(e -> log.error("统计已发布文章数量失败: 错误信息={}", e.getMessage()));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Posts> getArticlesByUserId(Long userId, int page, int size) {
        log.debug("获取用户文章列表: 用户ID={}, page={}, size={}", userId, page, size);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        return postsRepository.findByUserId(userId, pageRequest)
                .doOnComplete(() -> log.debug("获取用户文章列表完成: 用户ID={}", userId))
                .doOnError(e -> log.error("获取用户文章列表失败: 用户ID={}, 错误信息={}", userId, e.getMessage()));
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Long> countArticlesByUserId(Long userId) {
        log.debug("统计用户文章数量: 用户ID={}", userId);

        return postsRepository.countByUserId(userId)
                .doOnSuccess(count -> log.debug("用户文章数量: 用户ID={}, 数量={}", userId, count))
                .doOnError(e -> log.error("统计用户文章数量失败: 用户ID={}, 错误信息={}", userId, e.getMessage()));
    }


    @Override
    @CacheEvict(cacheNames = {
            CacheConstants.POST_DETAIL_CACHE_NAME,
            CacheConstants.POST_HOT_CACHE_NAME,
            CacheConstants.POST_FRONT_CACHE_NAME
    }, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #id", condition = "#id != null")
    public Mono<Integer> incrementLikes(Long id) {
        log.debug("增加文章点赞数: ID={}", id);

        return postsRepository.incrementLikes(id)
                .doOnSuccess(likes -> log.debug("文章点赞数增加成功: ID={}", id))
                .doOnError(e -> log.error("增加文章点赞数失败: ID={}, 错误信息={}", id, e.getMessage()));
    }

    @Override
    @CacheEvict(cacheNames = {
            CacheConstants.POST_DETAIL_CACHE_NAME,
            CacheConstants.POST_HOT_CACHE_NAME,
            CacheConstants.POST_FRONT_CACHE_NAME
    }, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #id", condition = "#id != null")
    public Mono<Integer> incrementComments(Long id) {
        log.debug("增加文章评论数: ID={}", id);

        return postsRepository.incrementComments(id)
                .doOnSuccess(comments -> log.debug("文章评论数增加成功: ID={}", id))
                .doOnError(e -> log.error("增加文章评论数失败: ID={}, 错误信息={}", id, e.getMessage()));
    }

    @Override
    @CacheEvict(cacheNames = {
            CacheConstants.POST_DETAIL_CACHE_NAME,
            CacheConstants.POST_HOT_CACHE_NAME,
            CacheConstants.POST_FRONT_CACHE_NAME
    }, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #id", condition = "#id != null")
    public Mono<Integer> decrementComments(Long id) {
        log.debug("减少文章评论数: ID={}", id);

        return postsRepository.decrementComments(id)
                .doOnSuccess(comments -> log.debug("文章评论数减少成功: ID={}", id))
                .doOnError(e -> log.error("减少文章评论数失败: ID={}, 错误信息={}", id, e.getMessage()));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_HOT_CACHE_NAME, key = "'" + CacheConstants.POST_HOT_KEY + "' + #limit", unless = "#result == null")
    public Flux<Posts> getHotArticles(int limit) {
        log.debug("获取热门文章: limit={}", limit);

        return postsRepository.findHotPosts(limit)
                .doOnComplete(() -> log.debug("获取热门文章完成: limit={}", limit))
                .doOnError(e -> log.error("获取热门文章失败: limit={}, 错误信息={}", limit, e.getMessage()));
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<PageResult<PostAdminListVO>> getArticlePageVO(int page, int size, String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime) {
        log.debug("分页查询文章VO: page={}, size={}, title={}, status={}, categoryId={}, tagId={}, startTime={}, endTime={}",
                page, size, title, status, categoryId, tagId, startTime, endTime);

        String titleParam = StringUtils.hasText(title) ? title : null;
        String startTimeParam = StringUtils.hasText(startTime) ? startTime : null;
        String endTimeParam = StringUtils.hasText(endTime) ? endTime : null;

        return postsRepository.countPostsByCondition(titleParam, status, categoryId, tagId, startTimeParam, endTimeParam)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(total -> buildPageResult(page, size, total, titleParam, status, categoryId, tagId, startTimeParam, endTimeParam))
                .doOnError(e -> log.error("分页查询文章VO失败: 错误信息={}", e.getMessage()));
    }

    /**
     * Task 7.1: 拆分长方法 - 构建分页结果
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
                .subscribeOn(Schedulers.boundedElastic())
                .collectList()
                .flatMap(postsList -> buildAdminListVOs(postsList, pageResult));
    }

    /**
     * Task 7.1: 拆分长方法 - 构建管理端列表VO
     */
    private Mono<PageResult<PostAdminListVO>> buildAdminListVOs(List<Posts> postsList, PageResult<PostAdminListVO> pageResult) {
        if (postsList.isEmpty()) {
            pageResult.setRecords(Collections.emptyList());
            return Mono.just(pageResult);
        }

        List<Long> postIds = postsList.stream().map(Posts::getId).collect(Collectors.toList());
        List<Long> userIds = postsList.stream().map(Posts::getUserId).distinct().collect(Collectors.toList());

        Mono<Map<Long, Tuple2<Long, String>>> categoriesMonoMap = 
                batchGetArticleCategories(postIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, Tuple2<String, String>>> usersMonoMap = 
                batchGetUserInfo(userIds).subscribeOn(Schedulers.parallel());

        return Mono.zip(categoriesMonoMap, usersMonoMap)
                .map(tuple -> {
                    Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                    Map<Long, Tuple2<String, String>> userMap = tuple.getT2();

                    List<PostAdminListVO> voList = postsList.stream()
                            .map(post -> buildPostAdminListVO(post, categoryMap, userMap))
                            .collect(Collectors.toList());

                    pageResult.setRecords(voList);
                    return pageResult;
                });
    }

    /**
     * Task 7.1: 拆分长方法 - 构建单个管理端列表VO
     */
    private PostAdminListVO buildPostAdminListVO(Posts post,
                                                  Map<Long, Tuple2<Long, String>> categoryMap,
                                                  Map<Long, Tuple2<String, String>> userMap) {
        PostAdminListVO vo = postMapper.toAdminListVO(post);
        Long postId = post.getId();
        Long userId = post.getUserId();

        Long categoryIdValue = null;
        String categoryName = null;
        if (categoryMap.containsKey(postId)) {
            Tuple2<Long, String> category = categoryMap.get(postId);
            categoryIdValue = category.getT1();
            categoryName = category.getT2();
        }

        String authorName = null;
        if (userMap.containsKey(userId)) {
            Tuple2<String, String> userInfo = userMap.get(userId);
            authorName = userInfo.getT1();
        }

        return postMapper.setAdminExtraProperties(vo, categoryIdValue, categoryName, authorName);
    }

    /**
     * 批量获取文章分类信息 - 使用自定义Repository批量查询
     * Task 7.2: 优化批量查询方法
     *
     * @param postIds 文章ID列表
     * @return 文章ID到分类信息(ID, 名称)的映射
     */
    private Mono<Map<Long, Tuple2<Long, String>>> batchGetArticleCategories(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return postsRepository.findPostsWithCategory(postIds)
                .filter(projection -> projection.getPostId() != null 
                        && projection.getCategoryId() != null 
                        && projection.getCategoryName() != null)
                .collectMap(
                    projection -> projection.getPostId(),
                    projection -> Tuples.of(projection.getCategoryId(), projection.getCategoryName())
                )
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    log.error("批量获取文章分类信息失败: {}", e.getMessage());
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * 批量获取用户信息 - 使用批量查询优化
     * Task 7.2: 优化批量查询方法
     *
     * @param userIds 用户ID列表
     * @return 用户ID到用户信息(名称, 头像)的映射
     */
    private Mono<Map<Long, Tuple2<String, String>>> batchGetUserInfo(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return Flux.fromIterable(userIds)
                .flatMap(userId -> getUserInfo(userId).map(userInfo -> Tuples.of(userId, userInfo)))
                .collectMap(Tuple2::getT1, Tuple2::getT2)
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    log.error("批量获取用户信息失败: {}", e.getMessage());
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * 批量获取文件URL - 使用批量查询优化
     * Task 7.2: 优化批量查询方法
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到URL的映射
     */
    private Mono<Map<Long, String>> batchGetFileUrls(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return fileService.getBatchFilePermanentUrls(fileIds)
                .map(urlMap -> {
                    Map<Long, String> result = new HashMap<>(urlMap);
                    fileIds.forEach(fileId -> result.putIfAbsent(fileId, DEFAULT_COVER_IMAGE));
                    return result;
                })
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    log.error("批量获取文件URL失败: {}", e.getMessage());
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * 获取用户信息
     *
     * @param userId 用户ID
     * @return 用户名称和头像的元组
     */
    private Mono<Tuple2<String, String>> getUserInfo(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String userName = user.getNickname() != null ? user.getNickname() : user.getUsername();
                    String avatarUrl = user.getAvatar();
                    if (avatarUrl == null || avatarUrl.isEmpty()) {
                        avatarUrl = DEFAULT_AVATAR;
                    }
                    return Tuples.of(userName, avatarUrl);
                })
                .switchIfEmpty(Mono.just(Tuples.of(UNKNOWN_USER, DEFAULT_AVATAR)));
    }

    /**
     * Task 7.1: 提取公共逻辑 - 从文章列表中提取各种ID
     *
     * @param posts 文章列表
     * @return 包含文章ID、用户ID、封面图片ID的元组
     */
    private Tuple2<List<Long>, Tuple2<List<Long>, List<Long>>> extractIdsFromPosts(List<Posts> posts) {
        List<Long> postIds = posts.stream().map(Posts::getId).collect(Collectors.toList());
        List<Long> userIds = posts.stream().map(Posts::getUserId).distinct().collect(Collectors.toList());
        List<Long> coverImageIds = posts.stream()
                .filter(post -> post.getCoverImageId() != null)
                .map(Posts::getCoverImageId)
                .distinct()
                .collect(Collectors.toList());
        return Tuples.of(postIds, Tuples.of(userIds, coverImageIds));
    }

    /**
     * Task 7.1: 提取公共逻辑 - 并行获取文章关联数据
     *
     * @param postIds 文章ID列表
     * @param userIds 用户ID列表
     * @param coverImageIds 封面图片ID列表
     * @return 包含分类、标签、用户、统计、封面图片信息的元组
     */
    private Mono<reactor.util.function.Tuple5<Map<Long, Tuple2<Long, String>>, Map<Long, List<String>>, Map<Long, Tuple2<String, String>>, Map<Long, Tuple2<Integer, Integer>>, Map<Long, String>>> batchGetPostRelatedData(
            List<Long> postIds, List<Long> userIds, List<Long> coverImageIds) {
        
        Mono<Map<Long, Tuple2<Long, String>>> categoriesMonoMap = 
                batchGetArticleCategories(postIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, List<String>>> tagsMonoMap = 
                batchGetArticleTags(postIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, Tuple2<String, String>>> usersMonoMap = 
                batchGetUserInfo(userIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, Tuple2<Integer, Integer>>> statsMonoMap = 
                batchGetArticleStats(postIds).subscribeOn(Schedulers.parallel());
        Mono<Map<Long, String>> coverMonoMap = 
                batchGetFileUrls(coverImageIds).subscribeOn(Schedulers.parallel());

        return Mono.zip(categoriesMonoMap, tagsMonoMap, usersMonoMap, statsMonoMap, coverMonoMap);
    }

    /**
     * Task 7.1: 提取公共逻辑 - 将文章实体转换为前台列表VO并填充额外信息
     *
     * @param post 文章实体
     * @param categoryMap 分类映射
     * @param tagMap 标签映射
     * @param userMap 用户映射
     * @param statsMap 统计映射
     * @param coverMap 封面图片映射
     * @return 填充完整的前台列表VO
     */
    private PostFrontListVO buildPostFrontListVO(Posts post,
                                                  Map<Long, Tuple2<Long, String>> categoryMap,
                                                  Map<Long, List<String>> tagMap,
                                                  Map<Long, Tuple2<String, String>> userMap,
                                                  Map<Long, Tuple2<Integer, Integer>> statsMap,
                                                  Map<Long, String> coverMap) {
        PostFrontListVO vo = postMapper.toFrontListVO(post);
        Long pid = post.getId();
        Long userId = post.getUserId();

        Long categoryIdValue = null;
        String categoryName = null;
        if (categoryMap.containsKey(pid)) {
            Tuple2<Long, String> category = categoryMap.get(pid);
            categoryIdValue = category.getT1();
            categoryName = category.getT2();
        }

        List<String> tags = tagMap.getOrDefault(pid, List.of());

        String authorName = null;
        String authorAvatar = null;
        if (userMap.containsKey(userId)) {
            Tuple2<String, String> userInfo = userMap.get(userId);
            authorName = userInfo.getT1();
            authorAvatar = userInfo.getT2();
        }

        Integer commentCount = 0;
        Integer likeCount = 0;
        if (statsMap.containsKey(pid)) {
            Tuple2<Integer, Integer> stats = statsMap.get(pid);
            commentCount = stats.getT1();
            likeCount = stats.getT2();
        }

        String coverImageUrl = null;
        Long coverImageId = post.getCoverImageId();
        if (coverImageId != null && coverMap.containsKey(coverImageId)) {
            coverImageUrl = coverMap.get(coverImageId);
        }

        return postMapper.setFrontExtraProperties(vo, categoryIdValue, categoryName, tags,
                authorName, authorAvatar, coverImageUrl, commentCount, likeCount);
    }

    /**
     * 获取图片URL
     *
     * @param imageId 图片ID
     * @return 图片URL
     */
    private Mono<String> getImageUrl(Long imageId) {
        if (imageId == null) {
            return Mono.just(DEFAULT_COVER_IMAGE);
        }

        return batchGetFileUrls(Collections.singletonList(imageId))
                .map(urlMap -> urlMap.getOrDefault(imageId, DEFAULT_COVER_IMAGE));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_DETAIL_CACHE_NAME, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #id", unless = "#result == null")
    public Mono<PostDetailVO> getArticleDetailVO(Long id) {
        log.info("获取文章详情VO: ID={}", id);

        return getArticleDetailVOFromDB(id)
                .doOnSuccess(detailVO -> log.debug("获取文章详情VO成功: ID={}", id))
                .doOnError(e -> log.error("获取文章详情VO失败: ID={}, 错误信息={}", id, e.getMessage()));
    }

    /**
     * 从数据库获取文章详情VO
     *
     * @param id 文章ID
     * @return 文章详情VO
     */
    private Mono<PostDetailVO> getArticleDetailVOFromDB(Long id) {
        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(article -> {
                    // 检查文章是否已删除
                    if (Objects.equals(SystemConstants.IS_DELETED, article.getIsDeleted())) {
                        return Mono.error(BusinessException.postAlreadyDeleted());
                    }

                    // 使用MapStruct转换为VO
                    PostDetailVO detailVO = postMapper.toDetailVO(article);

                    // 获取文章分类ID
                    Mono<List<Long>> categoryIdsMono = getArticleCategoryIds(article.getId())
                            .collectList();

                    // 获取文章标签ID
                    Mono<List<Long>> tagIdsMono = tagRepository.findByPostId(article.getId())
                            .map(Tag::getId)
                            .collectList();

                    // 获取封面图片URL
                    Mono<String> coverImageUrlMono = getImageUrl(article.getCoverImageId());

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
                            log.debug("文章[{}]SEO元数据: 标题={}, 描述={}, 别名={}", article.getId(), seoTitle, seoDescription, slug);
                        } catch (Exception e) {
                            log.error("解析SEO元数据失败: {}", e.getMessage());
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
                                detailVO.setSlug(finalSlug);

                                log.debug("文章详情VO构建完成: ID={}, 标题={}, 分类ID={}, 标签数={}",
                                        detailVO.getId(), detailVO.getTitle(), detailVO.getCategoryId(),
                                        detailVO.getTagsIds() != null ? detailVO.getTagsIds().size() : 0);

                                return detailVO;
                            });
                });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_FRONT_CACHE_NAME, key = "'" + CacheConstants.POST_FRONT_KEY + "' + #cursor + ':' + #limit + ':' + #createTime + ':' + #direction", unless = "#result.isEmpty()")
    public Mono<List<PostFrontListVO>> getFrontArticlesVO(String cursor, int limit, String createTime, String direction) {
        log.debug("前台游标分页查询文章VO: cursor={}, limit={}, createTime={}, direction={}", cursor, limit, createTime, direction);

        String cursorParam = StringUtils.hasText(cursor) ? cursor : null;
        String createTimeParam = StringUtils.hasText(createTime) ? createTime : null;
        String directionParam = validateDirection(direction);

        return postsRepository.findFrontPosts(cursorParam, limit + 1, createTimeParam, directionParam)
                .subscribeOn(Schedulers.boundedElastic())
                .collectList()
                .flatMap(articles -> buildFrontArticlesVOList(articles, limit))
                .doOnError(e -> log.error("前台游标分页查询文章VO失败: 错误信息={}", e.getMessage()));
    }

    /**
     * Task 7.1: 拆分长方法 - 验证方向参数
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
     * Task 7.1: 拆分长方法 - 构建前台文章VO列表
     */
    private Mono<List<PostFrontListVO>> buildFrontArticlesVOList(List<Posts> articles, int limit) {
        List<Posts> resultList = articles.size() > limit ? articles.subList(0, limit) : articles;
        
        if (resultList.isEmpty()) {
            return Mono.just(List.of());
        }

        Tuple2<List<Long>, Tuple2<List<Long>, List<Long>>> ids = extractIdsFromPosts(resultList);
        List<Long> postIds = ids.getT1();
        List<Long> userIds = ids.getT2().getT1();
        List<Long> coverImageIds = ids.getT2().getT2();

        return batchGetPostRelatedData(postIds, userIds, coverImageIds)
                .map(tuple -> {
                    Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                    Map<Long, List<String>> tagMap = tuple.getT2();
                    Map<Long, Tuple2<String, String>> userMap = tuple.getT3();
                    Map<Long, Tuple2<Integer, Integer>> statsMap = tuple.getT4();
                    Map<Long, String> coverMap = tuple.getT5();

                    return resultList.stream()
                            .map(post -> buildPostFrontListVO(post, categoryMap, tagMap, userMap, statsMap, coverMap))
                            .collect(Collectors.toList());
                });
    }

    /**
     * 批量获取文章标签信息 - 使用自定义Repository批量查询
     * Task 7.2: 优化批量查询方法
     *
     * @param postIds 文章ID列表
     * @return 文章ID到标签列表的映射
     */
    private Mono<Map<Long, List<String>>> batchGetArticleTags(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return postsRepository.findPostsWithTags(postIds)
                .filter(projection -> projection.getPostId() != null 
                        && projection.getTagId() != null 
                        && projection.getTagName() != null)
                .groupBy(projection -> projection.getPostId())
                .flatMap(group -> group.map(projection -> projection.getTagName())
                        .collectList()
                        .map(tags -> Tuples.of(group.key(), tags)))
                .collectMap(Tuple2::getT1, Tuple2::getT2)
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    log.error("批量获取文章标签信息失败: {}", e.getMessage());
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * 批量获取文章统计信息（评论数和点赞数）- 使用自定义Repository批量查询
     * Task 7.2: 优化批量查询方法
     *
     * @param postIds 文章ID列表
     * @return 文章ID到统计信息的映射
     */
    private Mono<Map<Long, Tuple2<Integer, Integer>>> batchGetArticleStats(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return postsRepository.countCommentsByPostIds(postIds)
                .collectMap(
                    map -> ((Number) map.get("post_id")).longValue(),
                    map -> Tuples.of(((Number) map.get("comment_count")).intValue(), 0)
                )
                .map(statsMap -> {
                    Map<Long, Tuple2<Integer, Integer>> result = new HashMap<>(statsMap);
                    postIds.forEach(postId -> result.putIfAbsent(postId, Tuples.of(0, 0)));
                    return result;
                })
                .defaultIfEmpty(postIds.stream().collect(Collectors.toMap(id -> id, id -> Tuples.of(0, 0))))
                .onErrorResume(e -> {
                    log.error("批量获取文章统计信息失败: {}", e.getMessage());
                    return Mono.just(postIds.stream().collect(Collectors.toMap(id -> id, id -> Tuples.of(0, 0))));
                });
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE_NAME, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #id"),
            @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE_NAME, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE_NAME, allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.POST_CACHE_NAME, key = "'" + CacheConstants.POST_RELATED_KEY + "' + #id + ':*'")
    })
    public Mono<Void> deleteArticle(Long id) {
        log.info("删除文章: ID={}", id);

        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(article -> {
                    // 检查文章是否已删除
                    if (Objects.equals(SystemConstants.IS_DELETED, article.getIsDeleted())) {
                        return Mono.error(BusinessException.postAlreadyDeleted());
                    }

                    article.setIsDeleted(SystemConstants.IS_DELETED);
                    return postsRepository.save(article)
                            .then(removeAllArticleCategories(id))
                            .then();
                })
                .doOnSuccess(v -> log.info("文章删除成功: ID={}", id))
                .doOnError(e -> {
                    if (e instanceof BusinessException) {
                        log.warn("文章删除失败: ID={}, 业务异常: {}", id, e.getMessage());
                    } else {
                        log.error("文章删除失败: ID={}, 错误信息={}", id, e.getMessage());
                    }
                });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
            CacheConstants.POST_FRONT_CACHE_NAME,
            CacheConstants.POST_HOT_CACHE_NAME
    }, allEntries = true)
    public Mono<Void> importMarkdownArticle(FilePart file, Long categoryId, Long userId) {
        log.info("导入Markdown文件: 文件名={}, 分类ID={}, 用户ID={}", file.filename(), categoryId, userId);

        // 使用非阻塞方式读取文件内容
        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .flatMap(markdownContent -> {
                    // 使用MarkdownUtils提取标题和摘要
                    String title = MarkdownUtils.extractTitle(markdownContent);
                    String excerpt = MarkdownUtils.extractExcerpt(markdownContent, 200);
                    
                    // 创建文章对象
                    PostCreateDTO postCreateDTO = new PostCreateDTO();
                    postCreateDTO.setTitle(title);
                    postCreateDTO.setContent(markdownContent);
                    postCreateDTO.setExcerpt(excerpt);
                    postCreateDTO.setIsOriginal(SystemConstants.YES);
                    postCreateDTO.setAllowComment(SystemConstants.YES);
                    postCreateDTO.setVisibility(Posts.Visibility.PUBLIC);
                    
                    // 创建文章
                    return createArticle(postCreateDTO, userId)
                            // 如果指定了分类，添加文章分类关联
                            .flatMap(article -> {
                                if (categoryId != null) {
                                    return addArticleCategory(article.getId(), categoryId)
                                            .then(Mono.just(article));
                                }
                                return Mono.just(article);
                            });
                })
                .then();
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<MarkdownExportVO> exportArticleToMarkdown(Long id) {
        log.info("导出文章为Markdown: ID={}", id);
        
        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("文章不存在")))
                .map(article -> {
                    // 1. 使用文章标题作为文件名
                    String title = StringUtils.hasText(article.getTitle()) ? article.getTitle() : UNNAMED_ARTICLE;
                    
                    // 替换文件名中的非法字符，但保留中文
                    // 只替换文件系统不允许的特殊字符，不替换中文
                    String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
                    
                    // 添加.md后缀
                    String filename = safeTitle + ".md";
                    
                    // 2. 直接使用博客内容作为Markdown内容
                    String content = article.getContent();
                    if (content == null) {
                        content = "";
                    }
                    
                    return MarkdownExportVO.builder()
                            .content(content)
                            .filename(filename)
                            .build();
                })
                .doOnSuccess(result -> log.info("导出文章为Markdown成功: ID={}, 文件名={}", id, result.getFilename()));
    }
}