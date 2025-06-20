package com.ryu.blog.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.*;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.PostMapper;
import com.ryu.blog.repository.*;
import com.ryu.blog.service.ArticleService;
import com.ryu.blog.service.ArticleVersionService;
import com.ryu.blog.service.ContentService;
import com.ryu.blog.service.FileService;
import com.ryu.blog.utils.MarkdownUtils;
import com.ryu.blog.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final ArticleVersionService articleVersionService;
    private final PostMapper postMapper;
    private final DatabaseClient databaseClient;
    private final ContentService contentService;
    private final FileService fileService;
    private final CacheManager cacheManager;
    
    private static final String ARTICLE_VIEW_COUNT_KEY = "article:view:count:";
    private static final String HOT_ARTICLES_KEY = "hot:articles";
    private static final String ARTICLE_DETAIL_KEY = "article:detail:";
    private static final Duration ARTICLE_DETAIL_CACHE_TTL = Duration.ofHours(2);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 处理SEO元数据并序列化为JSON字符串
     * @param seoTitle SEO标题
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

        if (seoDescription != null) {
            seoMeta.put("slug", seoDescription);
            }
        
            try {
            return new ObjectMapper().writeValueAsString(seoMeta);
            } catch (JsonProcessingException e) {
                log.error("SEO元数据序列化失败", e);
            return null;
        }
    }

    /**
     * 添加文章标签关联
     * 
     * @param articleId 文章ID
     * @param tagIds 标签ID列表
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
    public Mono<Posts> createArticle(PostCreateDTO articleCreateDTO, Long userId) {
        log.info("创建文章: 标题={}", articleCreateDTO.getTitle());
        
        Posts article = new Posts();
        article.setTitle(articleCreateDTO.getTitle());
        article.setContent(articleCreateDTO.getContent());
        article.setExcerpt(articleCreateDTO.getExcerpt());
        article.setCoverImageId(articleCreateDTO.getCoverImageId());
        article.setStatus(Posts.Status.DRAFT);
        article.setIsOriginal(articleCreateDTO.getIsOriginal() != null ? articleCreateDTO.getIsOriginal() : true);


        article.setSort(articleCreateDTO.getSort() != null ? articleCreateDTO.getSort() : 0);
        article.setAllowComment(articleCreateDTO.getAllowComment() != null ? articleCreateDTO.getAllowComment() : true);
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
        
        article.setVisibility(articleCreateDTO.getVisibility() != null ? articleCreateDTO.getVisibility() : "public");
        article.setPassword(articleCreateDTO.getPassword());
        article.setIsDeleted(0);
        
        // 处理SEO元数据
        String seoMeta = processSeoMetadata(articleCreateDTO.getSeoTitle(), articleCreateDTO.getSeoDescription());
        article.setSeoMeta(seoMeta);
        
        // 处理摘要
        String excerpt = articleCreateDTO.getExcerpt();
        Mono<Posts> articleMono;
        
        if (StringUtils.isEmpty(excerpt)) {
            // 如果摘要为空，自动生成摘要
            articleMono = contentService.generateExcerpt(articleCreateDTO.getContent(), 200)
                .flatMap(generatedExcerpt -> {
                    article.setExcerpt(generatedExcerpt);
                    return postsRepository.save(article);
                });
        } else {
            // 直接保存文章
            articleMono = postsRepository.save(article);
        }
        
        // 保存文章及关联数据
        return articleMono
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
                .flatMap(updatedArticle -> 
                    // 更新成功后清除缓存
                    clearArticleCache(updatedArticle.getId())
                        .thenReturn(updatedArticle)
                )
                .doOnSuccess(updatedArticle -> log.info("文章更新成功: ID={}, 标题={}", updatedArticle.getId(), updatedArticle.getTitle()))
                .doOnError(e -> log.error("文章更新失败: ID={}, 错误信息={}", articleUpdateDTO.getId(), e.getMessage()));
    }
    
    @Override
    @Transactional
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


    
    /**
     * 清除文章相关的缓存
     * 
     * @param articleId 文章ID
     * @return Void
     */
    private Mono<Void> clearArticleCache(Long articleId) {
        if (articleId == null) {
            return Mono.empty();
        }
        
        log.debug("清除文章相关缓存: ID={}", articleId);
        
        return Mono.when(
            // 清除文章详情缓存
            reactiveRedisTemplate.delete(ARTICLE_DETAIL_KEY + articleId)
                .doOnSuccess(result -> log.debug("清除文章详情缓存: ID={}, 结果={}", articleId, result)),
            
            // 清除文章浏览量缓存
            reactiveRedisTemplate.delete(ARTICLE_VIEW_COUNT_KEY + articleId)
                .doOnSuccess(result -> log.debug("清除文章浏览量缓存: ID={}, 结果={}", articleId, result)),
            
            // 清除热门文章缓存
            reactiveRedisTemplate.delete(HOT_ARTICLES_KEY)
                .doOnSuccess(result -> log.debug("清除热门文章缓存, 结果={}", result)),
                
            // 清除相关文章缓存（模式匹配删除）
            reactiveRedisTemplate.keys("*:related:" + articleId + ":*")
                .flatMap(key -> reactiveRedisTemplate.delete(key))
                .collectList()
                .doOnSuccess(result -> log.debug("清除相关文章缓存, 结果={}", result.size())),
                
            // 清除前台文章列表缓存（可能包含该文章）
            reactiveRedisTemplate.keys("*:front:*")
                .flatMap(key -> reactiveRedisTemplate.delete(key))
                .collectList()
                .doOnSuccess(result -> log.debug("清除前台文章列表缓存, 结果={}", result.size())),
                
            // 清除后台文章列表缓存
            reactiveRedisTemplate.keys("*:admin:page:*")
                .flatMap(key -> reactiveRedisTemplate.delete(key))
                .collectList()
                .doOnSuccess(result -> log.debug("清除后台文章列表缓存, 结果={}", result.size())),
                
            // 清除Spring Cache中的缓存
            Mono.fromRunnable(() -> {
                try {
                    // 清除详情缓存
                    cacheManager.getCache("postDetailCache").evict("detail:" + articleId);
                    // 清除热门文章缓存
                    cacheManager.getCache("postHotCache").clear();
                    // 清除前台文章列表缓存
                    cacheManager.getCache("postFrontCache").clear();
                    // 清除后台文章列表缓存
                    cacheManager.getCache("postAdminCache").clear();
                    log.debug("清除Spring Cache中的文章缓存成功: ID={}", articleId);
                } catch (Exception e) {
                    log.error("清除Spring Cache中的文章缓存失败: ID={}, 错误={}", articleId, e.getMessage());
                }
            })
        )
        .doOnError(e -> log.error("清除文章缓存失败: ID={}, 错误={}", articleId, e.getMessage()))
        .then();
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
        final int BATCH_SIZE = 50; // 每批处理的最大数量
        List<List<Long>> batches = new ArrayList<>();
        
        for (int i = 0; i < longIds.size(); i += BATCH_SIZE) {
            batches.add(longIds.subList(i, Math.min(i + BATCH_SIZE, longIds.size())));
        }
        
        // 逐批处理
        return Flux.fromIterable(batches)
                .flatMap(batchIds -> {
                    // 1. 首先检索所有存在且未删除的文章
                    String idListStr = batchIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
                    
                    String query = "SELECT id, title FROM t_posts WHERE id IN (" + idListStr + ") AND is_deleted = 0";
                    
                    return databaseClient.sql(query)
                            .map((row, rowMetadata) -> {
                                Long id = row.get("id", Long.class);
                                String title = row.get("title", String.class);
                                return Tuples.of(id, title);
                            })
                            .all()
                            .collectList()
                            .flatMap(articlesToDelete -> {
                                if (articlesToDelete.isEmpty()) {
                                    log.debug("批次中没有可删除的文章");
                                return Mono.empty();
                            }
                            
                                // 提取要删除的文章ID
                                List<Long> idsToDelete = articlesToDelete.stream()
                                        .map(Tuple2::getT1)
                                        .collect(Collectors.toList());
                                
                                log.debug("执行批量软删除: 数量={}", idsToDelete.size());
                                
                                // 2. 批量更新文章为已删除状态
                                String updateQuery = "UPDATE t_posts SET is_deleted = 1, update_time = NOW() WHERE id IN (" + 
                                        idsToDelete.stream().map(String::valueOf).collect(Collectors.joining(",")) + 
                                        ")";
                                
                                Mono<Integer> updateMono = databaseClient.sql(updateQuery)
                                        .fetch()
                                        .rowsUpdated()
                                        .map(Long::intValue);
                                
                                // 3. 批量删除文章分类关联
                                String deleteRelationsQuery = "DELETE FROM t_post_categories WHERE post_id IN (" + 
                                        idsToDelete.stream().map(String::valueOf).collect(Collectors.joining(",")) + 
                                        ")";
                                
                                Mono<Integer> deleteRelationsMono = databaseClient.sql(deleteRelationsQuery)
                                        .fetch()
                                        .rowsUpdated()
                                        .map(Long::intValue);
                                
                                // 4. 清除缓存
                                Mono<Void> clearCacheMono = Flux.fromIterable(idsToDelete)
                                        .flatMap(this::clearArticleCache)
                                        .then();
                                
                                // 5. 并行执行更新、删除和缓存清除操作
                                return Mono.zip(updateMono, deleteRelationsMono)
                                        .flatMap(results -> {
                                            int updatedCount = results.getT1();
                                            int deletedRelations = results.getT2();
                                            log.debug("批量删除结果: 更新文章状态={}, 删除关联记录={}", updatedCount, deletedRelations);
                                            
                                            // 记录每篇文章的删除情况
                                            articlesToDelete.forEach(article -> 
                                                log.debug("文章已删除: ID={}, 标题={}", article.getT1(), article.getT2())
                                            );
                                            
                                            // 清除缓存
                                            return clearCacheMono;
                                        })
                                        .onErrorResume(e -> {
                                            log.error("批量删除操作失败: {}", e.getMessage());
                                            return Mono.error(new BusinessException("批量删除操作失败: " + e.getMessage()));
                                        });
                            });
                })
                .then()
                .doOnSuccess(v -> {
                    log.info("批量删除文章完成: 请求删除数量={}", longIds.size());
                    // 清除前台文章列表和热门文章缓存
                    reactiveRedisTemplate.keys("*:front:*")
                        .flatMap(key -> reactiveRedisTemplate.delete(key))
                        .collectList()
                        .subscribe(result -> log.debug("清除前台文章列表缓存, 结果={}", result.size()));
                    
                    reactiveRedisTemplate.keys("*:hot:*")
                        .flatMap(key -> reactiveRedisTemplate.delete(key))
                        .collectList()
                        .subscribe(result -> log.debug("清除热门文章缓存, 结果={}", result.size()));
                    
                    reactiveRedisTemplate.keys("*:admin:page:*")
                        .flatMap(key -> reactiveRedisTemplate.delete(key))
                        .collectList()
                        .subscribe(result -> log.debug("清除管理后台文章列表缓存, 结果={}", result.size()));
                })
                .doOnError(e -> {
                    if (e instanceof BusinessException) {
                        log.warn("批量删除文章失败: 业务异常: {}", e.getMessage());
                    } else {
                        log.error("批量删除文章失败: 错误信息={}", e.getMessage());
                    }
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getArticlePage(int page, int size, String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime) {
        log.debug("分页查询文章: page={}, size={}, title={}, status={}, categoryId={}, tagId={}, startTime={}, endTime={}", 
                 page, size, title, status, categoryId, tagId, startTime, endTime);
        
        // 使用getArticlePageVO方法获取分页结果，然后转换为Map格式
        return getArticlePageVO(page, size, title, status, categoryId, tagId, startTime, endTime)
                .map(pageResult -> {
        Map<String, Object> result = new HashMap<>();
                    result.put("records", pageResult.getRecords());
                    result.put("total", pageResult.getTotal());
                    result.put("size", pageResult.getSize());
                    result.put("current", pageResult.getCurrent());
                    result.put("pages", pageResult.getPages());
                    return result;
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getFrontArticles(String cursor, int limit, String createTime, String direction) {
        log.debug("前台游标分页查询文章: cursor={}, limit={}, createTime={}, direction={}", 
                 cursor, limit, createTime, direction);
        
        // 复用getFrontArticlesVO方法
        return getFrontArticlesVO(cursor, limit, createTime, direction)
                .map(voList -> {
        Map<String, Object> result = new HashMap<>();
        
                    // 构建结果
                    result.put("records", voList);
                    
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
                        ((List<?>)r.get("records")).size(), r.get("hasMore")))
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
                    
                    // 获取所有文章ID
                    List<Long> postIds = postsList.stream()
                            .map(Posts::getId)
                            .collect(Collectors.toList());
                    
                    // 获取所有作者ID
                    List<Long> userIds = postsList.stream()
                            .map(Posts::getUserId)
                            .distinct()
                            .collect(Collectors.toList());
                    
                    // 获取所有封面图片ID
                    List<Long> coverImageIds = postsList.stream()
                            .filter(post -> post.getCoverImageId() != null)
                            .map(Posts::getCoverImageId)
                            .distinct()
                            .collect(Collectors.toList());
                    
                    // 批量查询分类、标签、作者信息、统计信息和封面图片
                    Mono<Map<Long, Tuple2<Long, String>>> categoriesMonoMap = batchGetArticleCategories(postIds);
                    Mono<Map<Long, List<String>>> tagsMonoMap = batchGetArticleTags(postIds);
                    Mono<Map<Long, Tuple2<String, String>>> usersMonoMap = batchGetUserInfo(userIds);
                    Mono<Map<Long, Tuple2<Integer, Integer>>> statsMonoMap = batchGetArticleStats(postIds);
                    Mono<Map<Long, String>> coverMonoMap = batchGetFileUrls(coverImageIds);
                    
                    return Mono.zip(
                        categoriesMonoMap,
                        tagsMonoMap,
                        usersMonoMap,
                        statsMonoMap,
                        coverMonoMap
                    )
                    .flatMapMany(tuple -> {
                        Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                        Map<Long, List<String>> tagMap = tuple.getT2();
                        Map<Long, Tuple2<String, String>> userMap = tuple.getT3();
                        Map<Long, Tuple2<Integer, Integer>> statsMap = tuple.getT4();
                        Map<Long, String> coverMap = tuple.getT5();
                        
                        // 转换并填充VO的信息
                        return Flux.fromIterable(postsList)
                                .map(post -> {
                                    PostFrontListVO vo = postMapper.toFrontListVO(post);
                                    Long pid = post.getId();
                                    Long userId = post.getUserId();
                                    
                                    // 准备设置额外属性所需的数据
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
                                    
                                    // 使用新的方法设置额外属性
                                    return postMapper.setFrontExtraProperties(
                                        vo,
                                        categoryIdValue,
                                        categoryName,
                                        tags,
                                        authorName,
                                        authorAvatar,
                                        coverImageUrl,
                                        commentCount,
                                        likeCount
                                    );
                                });
                            });
                })
                .doOnComplete(() -> log.debug("获取相关博客推荐VO完成: 文章ID={}", postId));
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
    public Mono<Integer> incrementViews(Long id) {
        log.debug("增加文章浏览量: ID={}", id);
        
        if (id == null) {
            log.warn("增加文章浏览量: 文章ID为空");
            return Mono.just(0);
        }
        
        final String cacheKey = ARTICLE_VIEW_COUNT_KEY + id;
        
        // 使用Redis原子操作增加浏览量
        return reactiveRedisTemplate.opsForValue().increment(cacheKey)
                .defaultIfEmpty(1L)  // 如果key不存在，Redis会创建并设为1
                .cast(Long.class)
                .flatMap(newViews -> {
                    // 设置7天过期时间
                    Mono<Boolean> setExpireMono = reactiveRedisTemplate.expire(cacheKey, Duration.ofDays(7))
                            .doOnError(e -> log.warn("设置浏览量计数器过期时间失败: ID={}, 错误={}", id, e.getMessage()));
                    
                    // 每10次浏览同步到数据库
                    Mono<Integer> syncToDbMono;
                    if (newViews % 10 == 0) {
                        log.debug("同步浏览量到数据库: ID={}, 浏览量={}", id, newViews);
                        // 直接更新数据库中的浏览量为Redis中的值，避免并发问题
                        syncToDbMono = postsRepository.updateViews(id, newViews.intValue())
                                .doOnSuccess(result -> {
                                    if (result > 0) {
                                        log.debug("数据库浏览量更新成功: ID={}, 新浏览量={}", id, newViews);
                                    } else {
                                        log.warn("数据库浏览量更新无影响: ID={}, 可能文章不存在", id);
                                    }
                                })
                            .onErrorResume(e -> {
                                    log.error("同步浏览量到数据库失败: ID={}, 错误={}", id, e.getMessage());
                                    return Mono.just(0);
                                });
                                    } else {
                        syncToDbMono = Mono.just(0);
                    }
                    
                    // 并行执行设置过期时间和可能的数据库同步
                    return Mono.zip(
                            setExpireMono.onErrorReturn(false),
                            syncToDbMono
                        )
                        .map(tuple -> newViews.intValue())
                        .onErrorResume(e -> {
                            log.error("处理浏览量增加操作失败: ID={}, 错误={}", id, e.getMessage());
                            return Mono.just(newViews.intValue());
                        });
                })
                .doOnSuccess(views -> log.debug("文章浏览量增加成功: ID={}, 当前浏览量={}", id, views))
                .onErrorResume(e -> {
                    log.error("Redis浏览量更新失败，尝试直接更新数据库: ID={}, 错误={}", id, e.getMessage());
                    // Redis操作失败，直接增加数据库中的浏览量
                    return postsRepository.incrementViews(id)
                            .defaultIfEmpty(0);
                });
    }
    
    @Override
    public Mono<Integer> incrementLikes(Long id) {
        log.debug("增加文章点赞数: ID={}", id);
        
        return postsRepository.incrementLikes(id)
                .doOnSuccess(likes -> log.debug("文章点赞数增加成功: ID={}", id))
                .doOnError(e -> log.error("增加文章点赞数失败: ID={}, 错误信息={}", id, e.getMessage()));
    }
    
    @Override
    public Mono<Integer> incrementComments(Long id) {
        log.debug("增加文章评论数: ID={}", id);
        
        return postsRepository.incrementComments(id)
                .doOnSuccess(comments -> log.debug("文章评论数增加成功: ID={}", id))
                .doOnError(e -> log.error("增加文章评论数失败: ID={}, 错误信息={}", id, e.getMessage()));
    }
    
    @Override
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
        
        // 先尝试从Redis获取热门文章
        return reactiveRedisTemplate.opsForList().range(HOT_ARTICLES_KEY, 0, limit - 1)
                .cast(Long.class)
                .flatMap(postsRepository::findById)
                .collectList()
                .flatMapMany(cachedArticles -> {
                    if (!cachedArticles.isEmpty()) {
                        return Flux.fromIterable(cachedArticles);
                    }
                    
                    // 如果Redis中没有，则从数据库查询并缓存
                    return postsRepository.findHotPosts(limit)
                        .collectList()
                        .flatMapMany(hotArticles -> {
                            // 如果有热门文章，则缓存到Redis
                            if (!hotArticles.isEmpty()) {
                                List<Long> ids = hotArticles.stream()
                                        .map(Posts::getId)
                                        .collect(Collectors.toList());
                                
                                return reactiveRedisTemplate.opsForList().delete(HOT_ARTICLES_KEY)
                                        .thenMany(Flux.fromIterable(ids))
                                        .concatMap(id -> reactiveRedisTemplate.opsForList().rightPush(HOT_ARTICLES_KEY, id))
                                        .then(reactiveRedisTemplate.expire(HOT_ARTICLES_KEY, Duration.ofHours(24)))
                                        .thenMany(Flux.fromIterable(hotArticles));
                            }
                            return Flux.fromIterable(hotArticles);
                        });
                })
                .doOnComplete(() -> log.debug("获取热门文章完成: limit={}", limit))
                .doOnError(e -> log.error("获取热门文章失败: limit={}, 错误信息={}", limit, e.getMessage()));
    }

    /**
     * 获取状态描述
     * @param status 状态代码
     * @return 状态描述
     */
    private String getStatusDescription(Integer status) {
        switch (status) {
            case Posts.Status.DRAFT: return "草稿";
            case Posts.Status.PENDING: return "待发布";
            case Posts.Status.PUBLISHED: return "已发布";
            case Posts.Status.ARCHIVED: return "已归档";
            default: return "未知状态(" + status + ")";
        }
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_ADMIN_CACHE_NAME, key = "'" + CacheConstants.POST_ADMIN_KEY + "' + #page + ':' + #size + ':' + #title + ':' + #status + ':' + #categoryId + ':' + #tagId + ':' + #startTime + ':' + #endTime", unless = "#result == null")
    public Mono<PageResult<PostAdminListVO>> getArticlePageVO(int page, int size, String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime) {
        log.debug("分页查询文章VO: page={}, size={}, title={}, status={}, categoryId={}, tagId={}, startTime={}, endTime={}", 
                 page, size, title, status, categoryId, tagId, startTime, endTime);
        
        PageResult<PostAdminListVO> pageResult = new PageResult<>();
        
        // 查询条件
        String titleParam = StringUtils.hasText(title) ? title : null;
        String startTimeParam = StringUtils.hasText(startTime) ? startTime : null;
        String endTimeParam = StringUtils.hasText(endTime) ? endTime : null;
        
        // 查询总数 - 使用IO调度器
        return postsRepository.countPostsByCondition(
                titleParam, status, categoryId, tagId, startTimeParam, endTimeParam)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(total -> {
                    // 设置分页信息
                    pageResult.setTotal(total);
                    pageResult.setCurrent(page + 1); // 页码从0开始，展示时+1
                    pageResult.setSize(size);
                    pageResult.setPages((total + size - 1) / size); // 计算总页数
                    
                    if (total == 0) {
                        // 如果没有数据，直接返回空结果
                        pageResult.setRecords(Collections.emptyList());
                        return Mono.just(pageResult);
                    }
                    
                    // 先查询文章基本信息 - 使用IO调度器
                    return postsRepository.findPostsByCondition(
                            titleParam, status, categoryId, tagId, startTimeParam, endTimeParam, page * size, size)
                            .subscribeOn(Schedulers.boundedElastic())
                .collectList()
                            .flatMap(postsList -> {
                                if (postsList.isEmpty()) {
                                    pageResult.setRecords(Collections.emptyList());
                                    return Mono.just(pageResult);
                                }
                                
                    // 获取所有文章ID
                                List<Long> postIds = postsList.stream()
                            .map(Posts::getId)
                            .collect(Collectors.toList());
                    
                    // 获取所有作者ID
                                List<Long> userIds = postsList.stream()
                            .map(Posts::getUserId)
                            .distinct()
                            .collect(Collectors.toList());
                    
                                // 批量查询分类信息和作者信息 - 并行执行
                                Mono<Map<Long, Tuple2<Long, String>>> categoriesMonoMap = 
                                    batchGetArticleCategories(postIds)
                                        .subscribeOn(Schedulers.parallel());
                                
                                Mono<Map<Long, Tuple2<String, String>>> usersMonoMap = 
                                    batchGetUserInfo(userIds)
                                        .subscribeOn(Schedulers.parallel());
                                
                                // 并行获取数据
                    return Mono.zip(
                        categoriesMonoMap,
                                    usersMonoMap
                    )
                    .map(tuple -> {
                        Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                                    Map<Long, Tuple2<String, String>> userMap = tuple.getT2();
                                    
                                    // 转换并填充VO的信息
                                    List<PostAdminListVO> voList = postsList.stream()
                                            .map(post -> {
                                                PostAdminListVO vo = postMapper.toAdminListVO(post);
                            Long postId = post.getId();
                            Long userId = post.getUserId();
                            
                                                // 准备设置额外属性所需的数据
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
                                                
                                                // 使用MapStruct设置额外属性
                                                return postMapper.setAdminExtraProperties(
                                                    vo,
                                                    categoryIdValue,
                                                    categoryName,
                                                    authorName
                                                );
                                            })
                                            .collect(Collectors.toList());
                                    
                                    pageResult.setRecords(voList);
                                    return pageResult;
                                });
                            });
                })
                .doOnSuccess(result -> log.debug("分页查询文章VO成功: 总数={}", result.getTotal()))
                .doOnError(e -> log.error("分页查询文章VO失败: 错误信息={}", e.getMessage()));
    }
    
    /**
     * 批量获取文章分类信息 - 使用批量查询优化
     * 
     * @param postIds 文章ID列表
     * @return 文章ID到分类信息(ID,名称)的映射
     */
    private Mono<Map<Long, Tuple2<Long, String>>> batchGetArticleCategories(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        // 构建ID列表的字符串，用于IN查询
        String postIdsStr = postIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        // 使用手动SQL查询代替Repository方法，避免映射问题
        String query = "SELECT pc.post_id, c.id as category_id, c.name as category_name " +
                "FROM t_post_categories pc " +
                "LEFT JOIN t_categories c ON pc.category_id = c.id " +
                "WHERE pc.post_id IN (" + postIdsStr + ")";
        
        return databaseClient.sql(query)
                .map((row, rowMetadata) -> {
                    Long postId = row.get("post_id", Long.class);
                    Long categoryId = row.get("category_id", Long.class);
                    String categoryName = row.get("category_name", String.class);
                    return Tuples.of(postId, Tuples.of(categoryId, categoryName));
                })
                .all()
                .collectMap(
                    tuple -> tuple.getT1(), // 文章ID
                    tuple -> tuple.getT2()  // 分类信息(ID,名称)
                )
                .defaultIfEmpty(Collections.emptyMap())
                .doOnSuccess(map -> log.debug("批量获取文章分类信息成功: 文章数={}, 分类数={}", postIds.size(), map.size()))
                .onErrorResume(e -> {
                    log.error("批量获取文章分类信息失败: {}", e.getMessage(), e);
                    return Mono.just(Collections.emptyMap());
                });
    }
    
    /**
     * 批量获取用户信息 - 使用批量查询优化
     * 
     * @param userIds 用户ID列表
     * @return 用户ID到用户信息(名称,头像)的映射
     */
    private Mono<Map<Long, Tuple2<String, String>>> batchGetUserInfo(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        // 批量查询用户信息
        return Flux.fromIterable(userIds)
                .flatMap(userId -> 
                    // 使用已有的方法获取用户信息
                    getUserInfo(userId)
                        .map(userInfo -> Tuples.of(userId, userInfo))
                )
                .collectMap(
                    tuple -> tuple.getT1(), // 用户ID
                    tuple -> tuple.getT2()  // 用户信息(名称,头像)
                )
                .defaultIfEmpty(Collections.emptyMap());
    }
    
    /**
     * 批量获取文件URL - 使用批量查询优化
     * 
     * @param fileIds 文件ID列表
     * @return 文件ID到URL的映射
     */
    private Mono<Map<Long, String>> batchGetFileUrls(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        // 使用文件服务获取永久图片URL
        return fileService.getBatchFilePermanentUrls(fileIds)
                .defaultIfEmpty(Collections.emptyMap())
                .onErrorResume(e -> {
                    log.error("批量获取文件URL失败: {}", e.getMessage());
                    return Mono.just(Collections.emptyMap());
                })
                .map(urlMap -> {
                    // 对于不存在的图片，设置默认图片URL
                    Map<Long, String> result = new HashMap<>(urlMap);
                    fileIds.forEach(fileId -> {
                        if (!result.containsKey(fileId)) {
                            result.put(fileId, "/assets/images/default-cover.jpg");
                        }
                    });
                    return result;
                });
    }
    
    /**
     * 批量获取评论数量 - 使用批量查询优化
     * 
     * @param postIds 文章ID列表
     * @return 文章ID到评论数的映射
     */
    private Mono<Map<Long, Integer>> batchGetCommentCounts(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        // 查询每篇文章的评论数
        return Flux.fromIterable(postIds)
                .flatMap(postId -> 
                    // 使用已有的方法获取评论数
                    commentRepository.countByPostId(postId)
                        .map(count -> Tuples.of(postId, count.intValue()))
                )
                .collectMap(
                    tuple -> tuple.getT1(), // 文章ID
                    tuple -> tuple.getT2()  // 评论数
                )
                .defaultIfEmpty(Collections.emptyMap());
    }

    /**
     * 将Markdown内容转换为HTML
     * @param markdown Markdown格式的内容
     * @return HTML内容
     */
    private Mono<String> renderMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return Mono.just("");
        }
        return MarkdownUtils.renderHtmlReactive(markdown);
    }
    
    /**
     * 获取文章的分类ID和名称
     * @param postId 文章ID
     * @return 文章ID和分类信息的元组
     */
    private Flux<Tuple2<Long, Tuple2<Long, String>>> getArticleCategoryWithName(Long postId) {
        return postCategoryRepository.findByPostId(postId)
                .flatMap(postCategory -> {
                    Long categoryId = postCategory.getCategoryId();
                    // 查询分类表获取名称
                    return categoryRepository.findById(categoryId)
                            .map(category -> Tuples.of(postId, Tuples.of(categoryId, category.getName())))
                            .switchIfEmpty(Mono.just(Tuples.of(postId, Tuples.of(categoryId, "未知分类"))));
                });
    }
    
    /**
     * 获取分类名称
     * @param categoryId 分类ID
     * @return 分类名称
     */
    private Mono<String> getCategoryName(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .map(Category::getName)
                .switchIfEmpty(Mono.just("未知分类"));
    }
    
    /**
     * 获取文章的标签名称列表
     * @param postId 文章ID
     * @return 标签名称列表
     */
    private Flux<String> getArticleTagNames(Long postId) {
        // 查询文章标签关联表和标签表
        return tagRepository.findByPostId(postId)
                .map(Tag::getName)
                .switchIfEmpty(Flux.empty());
    }
    
    /**
     * 获取用户信息
     * @param userId 用户ID
     * @return 用户名称和头像的元组
     */
    private Mono<Tuple2<String, String>> getUserInfo(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String userName = user.getNickname() != null ? user.getNickname() : user.getUsername();
                    String avatarUrl = user.getAvatar();
                    // 如果头像URL为空，使用默认头像
                    if (avatarUrl == null || avatarUrl.isEmpty()) {
                        avatarUrl = "/assets/images/default-avatar.png";
                    }
                    return Tuples.of(userName, avatarUrl);
                })
                .switchIfEmpty(Mono.just(Tuples.of("未知用户", "/assets/images/default-avatar.png")));
    }
    
    /**
     * 获取文章的统计信息（评论数和点赞数）
     * 
     * @param postId 文章ID列表
     * @return 包含评论数和点赞数的元组
     */
    private Mono<Tuple2<Integer, Integer>> getArticleStats(Long postId) {
        // 查询评论表获取评论数
        Mono<Long> commentCountMono = commentRepository.countByPostId(postId)
                .defaultIfEmpty(0L);
        
        // 点赞数暂时固定为0，因为Posts实体中没有likes属性
        // 如果后续需要点赞功能，可以通过Redis或专门的点赞表来实现
        Mono<Integer> likeCountMono = Mono.just(0);
        
        // 组合结果
        return Mono.zip(
                commentCountMono.map(Long::intValue),
                likeCountMono
        );
    }
    
    /**
     * 获取图片URL
     * @param imageId 图片ID
     * @return 图片URL
     */
    private Mono<String> getImageUrl(Long imageId) {
        if (imageId == null) {
            return Mono.just("/assets/images/default-cover.jpg");
        }
        
        // 调用批量获取方法处理单个图片ID的情况
        return batchGetFileUrls(Collections.singletonList(imageId))
                .map(urlMap -> urlMap.getOrDefault(imageId, "/assets/images/default-cover.jpg"));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_DETAIL_CACHE_NAME, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #id", unless = "#result == null")
    public Mono<PostDetailVO> getArticleDetailVO(Long id) {
        log.info("获取文章详情VO: ID={}", id);
        
        // 先尝试从缓存获取
        return reactiveRedisTemplate.opsForValue().get(ARTICLE_DETAIL_KEY + id)
                .cast(PostDetailVO.class)
                .switchIfEmpty(
                    // 缓存未命中，从数据库获取并缓存
                    getArticleDetailVOFromDB(id)
                        .flatMap(detailVO -> 
                            // 缓存文章详情
                            reactiveRedisTemplate.opsForValue()
                                .set(ARTICLE_DETAIL_KEY + id, detailVO, ARTICLE_DETAIL_CACHE_TTL)
                                .thenReturn(detailVO)
                        )
                )
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
                    if (article.getIsDeleted() == 1) {
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
                            Map<String, String> seoMeta = new ObjectMapper().readValue(article.getSeoMeta(), Map.class);
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
                    
                    // 增加浏览量（异步操作，不阻塞当前流程）
                    incrementViews(article.getId())
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe(
                                    newViews -> log.debug("文章[{}]浏览量更新为: {}", article.getId(), newViews),
                                    error -> log.error("更新文章[{}]浏览量失败: {}", article.getId(), error.getMessage())
                            );
                    
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
                })
                .onErrorResume(e -> {
                    log.error("获取文章详情失败: ID={}, 错误={}", id, e.getMessage());
                    if (e instanceof BusinessException) {
                        return Mono.error(e);
                    }
                    return Mono.error(BusinessException.postNotFound());
                });
    }
    
    /**
     * 简化版获取文章基本信息方法，仅供内部使用
     * 
     * @param id 文章ID
     * @return 文章实体
     */
    private Mono<Posts> getArticleBasicInfo(Long id) {
        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .doOnSuccess(article -> log.debug("获取文章基本信息成功: ID={}, 标题={}", article.getId(), article.getTitle()))
                .doOnError(e -> log.error("获取文章基本信息失败: ID={}, 错误信息={}", id, e.getMessage()));
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.POST_FRONT_CACHE_NAME, key = "'" + CacheConstants.POST_FRONT_KEY + "' + #cursor + ':' + #limit + ':' + #createTime + ':' + #direction", unless = "#result.isEmpty()")
    public Mono<List<PostFrontListVO>> getFrontArticlesVO(String cursor, int limit, String createTime, String direction) {
        log.debug("前台游标分页查询文章VO: cursor={}, limit={}, createTime={}, direction={}", 
                 cursor, limit, createTime, direction);
        
        // 处理游标参数
        String cursorParam = StringUtils.hasText(cursor) ? cursor : null;
        
        // 处理创建时间参数
        String createTimeParam = null;
        if (StringUtils.hasText(createTime)) {
            // 确保时间格式正确
            try {
                LocalDateTime.parse(createTime, DATE_TIME_FORMATTER);
                createTimeParam = createTime;
            } catch (Exception e) {
                log.warn("创建时间格式错误: {}, 将使用默认值", createTime);
            }
        }
        
        // 处理方向参数
        String directionParam = StringUtils.hasText(direction) ? direction : "older";
        if (!directionParam.equals("newer") && !directionParam.equals("older") && !directionParam.equals("comprehensive")) {
            log.warn("方向参数错误: {}, 将使用默认值 'older'", direction);
            directionParam = "older";
        }
        
        // 查询参数 - 传递给Repository
        final String finalCreateTimeParam = createTimeParam;
        final String finalDirectionParam = directionParam;
        
        // 查询数据 - 请求多查询一条用于判断是否还有更多数据
        return postsRepository.findFrontPosts(cursorParam, limit + 1, finalCreateTimeParam, finalDirectionParam)
                .subscribeOn(Schedulers.boundedElastic()) // 使用IO调度器
                .collectList()
                .flatMap(articles -> {
                    // 检查是否有更多数据，但不返回这个信息，只返回文章列表
                    List<Posts> resultList = articles.size() > limit ? articles.subList(0, limit) : articles;
                    
                    if (resultList.isEmpty()) {
                        return Mono.just(List.<PostFrontListVO>of());
                    }
                    
                    // 获取所有文章ID
                    List<Long> postIds = resultList.stream()
                            .map(Posts::getId)
                            .collect(Collectors.toList());
                    
                    // 获取所有作者ID
                    List<Long> userIds = resultList.stream()
                            .map(Posts::getUserId)
                            .distinct()
                            .collect(Collectors.toList());
                    
                    // 获取所有封面图片ID
                    List<Long> coverImageIds = resultList.stream()
                            .filter(post -> post.getCoverImageId() != null)
                            .map(Posts::getCoverImageId)
                            .distinct()
                            .collect(Collectors.toList());
                    
                    // 并行查询各种关联数据
                    Mono<Map<Long, Tuple2<Long, String>>> categoriesMonoMap = 
                        batchGetArticleCategories(postIds)
                            .subscribeOn(Schedulers.parallel());
                    
                    Mono<Map<Long, List<String>>> tagsMonoMap = 
                        batchGetArticleTags(postIds)
                            .subscribeOn(Schedulers.parallel());
                    
                    Mono<Map<Long, Tuple2<String, String>>> usersMonoMap = 
                        batchGetUserInfo(userIds)
                            .subscribeOn(Schedulers.parallel());
                    
                    Mono<Map<Long, Tuple2<Integer, Integer>>> statsMonoMap = 
                        batchGetArticleStats(postIds)
                            .subscribeOn(Schedulers.parallel());
                    
                    Mono<Map<Long, String>> coverMonoMap = 
                        batchGetFileUrls(coverImageIds)
                            .subscribeOn(Schedulers.parallel());
                    
                    // 并行获取所有数据
                    return Mono.zip(
                        categoriesMonoMap,
                        tagsMonoMap,
                        usersMonoMap,
                        statsMonoMap,
                        coverMonoMap
                    )
                    .map(tuple -> {
                        Map<Long, Tuple2<Long, String>> categoryMap = tuple.getT1();
                        Map<Long, List<String>> tagMap = tuple.getT2();
                        Map<Long, Tuple2<String, String>> userMap = tuple.getT3();
                        Map<Long, Tuple2<Integer, Integer>> statsMap = tuple.getT4();
                        Map<Long, String> coverMap = tuple.getT5();
                        
                        // 填充VO的额外信息
                        List<PostFrontListVO> voList = new ArrayList<>(resultList.size());
                        
                        for (Posts post : resultList) {
                            PostFrontListVO vo = postMapper.toFrontListVO(post);
                            Long pid = post.getId();
                            Long userId = post.getUserId();
                            
                            // 准备设置额外属性所需的数据
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
                            
                            // 使用MapStruct设置额外属性
                            PostFrontListVO enrichedVo = postMapper.setFrontExtraProperties(
                                vo,
                                categoryIdValue,
                                categoryName,
                                tags,
                                authorName,
                                authorAvatar,
                                coverImageUrl,
                                commentCount,
                                likeCount
                            );
                            
                            voList.add(enrichedVo);
                        }
                        
                        return voList;
                    });
                })
                .doOnSuccess(voList -> log.debug("前台游标分页查询文章VO成功: 返回记录数={}", voList.size()))
                .doOnError(e -> log.error("前台游标分页查询文章VO失败: 错误信息={}", e.getMessage()));
    }

    /**
     * 批量获取文章标签信息
     * 
     * @param postIds 文章ID列表
     * @return 文章ID到标签列表的映射
     */
    private Mono<Map<Long, List<String>>> batchGetArticleTags(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        // 构建ID列表的字符串，用于IN查询
        String postIdsStr = postIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        // 使用手动SQL查询代替Repository方法，避免映射问题
        String query = "SELECT pt.post_id, t.name as tag_name " +
                "FROM t_post_tags pt " +
                "LEFT JOIN t_tags t ON pt.tag_id = t.id " +
                "WHERE pt.post_id IN (" + postIdsStr + ")";
        
        return databaseClient.sql(query)
                .map((row, rowMetadata) -> {
                    Long postId = row.get("post_id", Long.class);
                    String tagName = row.get("tag_name", String.class);
                    return Tuples.of(postId, tagName);
                })
                .all()
                .groupBy(tuple -> tuple.getT1()) // 按文章ID分组
                .flatMap(group -> {
                    Long postId = group.key();
                    return group.map(Tuple2::getT2) // 提取标签名称
                            .collectList()
                            .map(tags -> Tuples.of(postId, tags));
                })
                .collectMap(
                    tuple -> tuple.getT1(), // 文章ID
                    tuple -> tuple.getT2()  // 标签列表
                )
                .defaultIfEmpty(Collections.emptyMap())
                .doOnSuccess(map -> log.debug("批量获取文章标签信息成功: 文章数={}, 有标签的文章数={}", postIds.size(), map.size()))
                .onErrorResume(e -> {
                    log.error("批量获取文章标签信息失败: {}", e.getMessage(), e);
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * 批量获取文章统计信息（评论数和点赞数）
     * 
     * @param postIds 文章ID列表
     * @return 文章ID到统计信息的映射
     */
    private Mono<Map<Long, Tuple2<Integer, Integer>>> batchGetArticleStats(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        
        // 构建ID列表的字符串，用于IN查询
        String postIdsStr = postIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        // 使用手动SQL查询代替Repository方法，避免映射问题
        String query = "SELECT p.id as post_id, COUNT(c.id) as comment_count " +
                "FROM t_posts p " +
                "LEFT JOIN t_comments c ON p.id = c.post_id AND c.is_deleted = 0 " +
                "WHERE p.id IN (" + postIdsStr + ") " +
                "GROUP BY p.id";
        
        return databaseClient.sql(query)
                .map((row, rowMetadata) -> {
                    Long postId = row.get("post_id", Long.class);
                    Integer commentCount = row.get("comment_count", Integer.class);
                    // 目前点赞数固定为0，未来可以从Redis或专门的点赞表获取
                    return Tuples.of(postId, Tuples.of(commentCount != null ? commentCount : 0, 0));
                })
                .all()
                .collectMap(
                    tuple -> tuple.getT1(), // 文章ID
                    tuple -> tuple.getT2()  // 统计信息(评论数和点赞数)
                )
                .defaultIfEmpty(Collections.emptyMap())
                .map(statsMap -> {
                    // 为所有请求的文章ID添加默认值（如果没有评论的话）
                    Map<Long, Tuple2<Integer, Integer>> result = new HashMap<>(statsMap);
                    postIds.forEach(postId -> {
                        if (!result.containsKey(postId)) {
                            result.put(postId, Tuples.of(0, 0));
                        }
                    });
                    return result;
                })
                .doOnSuccess(map -> log.debug("批量获取文章统计信息成功: 文章数={}, 有评论的文章数={}", 
                        postIds.size(), map.values().stream().filter(t -> t.getT1() > 0).count()))
                .onErrorResume(e -> {
                    log.error("批量获取文章统计信息失败: {}", e.getMessage(), e);
                    return Mono.just(postIds.stream()
                            .collect(Collectors.toMap(
                                    id -> id,
                                    id -> Tuples.of(0, 0)
                            )));
                });
    }

    @Override
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.POST_DETAIL_CACHE_NAME, key = "'" + CacheConstants.POST_DETAIL_KEY + "' + #id"),
        @CacheEvict(cacheNames = CacheConstants.POST_FRONT_CACHE_NAME, key = "'" + CacheConstants.POST_FRONT_KEY + "'*'", allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.POST_HOT_CACHE_NAME, key = "'" + CacheConstants.POST_HOT_KEY + "'*'", allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.POST_CACHE_NAME, key = "'" + CacheConstants.POST_RELATED_KEY + "' + #id + ':*'", allEntries = true),
        @CacheEvict(cacheNames = CacheConstants.POST_ADMIN_CACHE_NAME, key = "'" + CacheConstants.POST_ADMIN_KEY + "'*'", allEntries = true)
    })
    public Mono<Void> deleteArticle(Long id) {
        log.info("删除文章: ID={}", id);
        
        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.postNotFound()))
                .flatMap(article -> {
                    // 检查文章是否已删除
                    if (article.getIsDeleted() == 1) {
                        return Mono.error(BusinessException.postAlreadyDeleted());
                    }
                    
                    article.setIsDeleted(1);
                    return postsRepository.save(article)
                            .then(removeAllArticleCategories(id))
                            .then(clearArticleCache(id)) // 清除缓存
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

    /**
     * 从Markdown文本中提取标题
     * 如果没有找到标题，返回默认标题
     * 
     * @param markdownContent Markdown内容
     * @return 提取的标题
     */
    private String extractTitleFromMarkdown(String markdownContent) {
        if (markdownContent == null || markdownContent.isEmpty()) {
            return "未命名文章";
        }
        
        // 尝试匹配第一个H1标题 (# 标题)
        Pattern h1Pattern = Pattern.compile("^\\s*# (.+)$", Pattern.MULTILINE);
        Matcher h1Matcher = h1Pattern.matcher(markdownContent);
        
        if (h1Matcher.find()) {
            return h1Matcher.group(1).trim();
        }
        
        // 没有找到H1标题，尝试提取第一行非空内容作为标题
        String[] lines = markdownContent.split("\\r?\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#") && !trimmedLine.startsWith("---")) {
                // 限制标题长度
                if (trimmedLine.length() > 100) {
                    return trimmedLine.substring(0, 97) + "...";
                }
                return trimmedLine;
            }
        }
        
        // 如果都没找到，返回默认标题
        return "未命名文章";
    }
    
    /**
     * 从Markdown文本中提取摘要
     * 如果没有找到合适的摘要，返回空字符串
     * 
     * @param markdownContent Markdown内容
     * @return 提取的摘要
     */
    private String extractExcerptFromMarkdown(String markdownContent) {
        if (markdownContent == null || markdownContent.isEmpty()) {
            return "";
        }
        
        // 删除代码块
        String contentWithoutCodeBlocks = markdownContent.replaceAll("```[\\s\\S]*?```", "");
        
        // 删除H1标题
        String contentWithoutH1 = contentWithoutCodeBlocks.replaceAll("^\\s*# .+$", "");
        
        // 提取第一个非空段落
        Pattern paragraphPattern = Pattern.compile("([\\p{L}\\p{N}][^\\n]+(?:\\n[^\\n]+)*)");
        Matcher paragraphMatcher = paragraphPattern.matcher(contentWithoutH1);
        
        if (paragraphMatcher.find()) {
            String excerpt = paragraphMatcher.group(1).trim();
            // 限制摘要长度
            if (excerpt.length() > 200) {
                return excerpt.substring(0, 197) + "...";
            }
            return excerpt;
        }
        
        return "";
    }

    @Override
    @Transactional
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
                    postCreateDTO.setIsOriginal(true);
                    postCreateDTO.setAllowComment(true);
                    postCreateDTO.setVisibility("public");
                    
                    // 创建文章
                    return createArticle(postCreateDTO, userId)
                            // 如果指定了分类，添加文章分类关联
                            .flatMap(article -> {
                                if (categoryId != null) {
                                    return addArticleCategory(article.getId(), categoryId)
                                            .then(Mono.just(article));
                                }
                                return Mono.just(article);
                            })
                            // 清除缓存
                            .flatMap(article -> clearArticleCache(article.getId()).thenReturn(article));
                })
                .then()
                .onErrorResume(e -> {
                    log.error("导入Markdown文件失败", e);
                    if (e instanceof BusinessException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new BusinessException("导入Markdown文件失败: " + e.getMessage()));
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<MarkdownExportVO> exportArticleToMarkdown(Long id) {
        log.info("导出文章为Markdown: ID={}", id);
        
        return postsRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("文章不存在")))
                .map(article -> {
                    // 1. 使用文章标题作为文件名
                    String title = StringUtils.hasText(article.getTitle()) ? article.getTitle() : "未命名文章";
                    
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
                .doOnSuccess(result -> log.info("导出文章为Markdown成功: ID={}, 文件名={}", id, result.getFilename()))
                .doOnError(e -> log.error("导出文章为Markdown失败: ID={}, 错误={}", id, e.getMessage()))
                .onErrorResume(e -> {
                    if (e instanceof BusinessException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new BusinessException("导出文章为Markdown失败: " + e.getMessage()));
                });
    }
}