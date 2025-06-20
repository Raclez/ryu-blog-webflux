package com.ryu.blog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.dto.CategoryCreateDTO;
import com.ryu.blog.dto.CategoryListDTO;
import com.ryu.blog.dto.CategoryUpdateDTO;
import com.ryu.blog.entity.Category;
import com.ryu.blog.entity.PostCategory;
import com.ryu.blog.mapper.CategoryMapper;
import com.ryu.blog.repository.CategoryRepository;
import com.ryu.blog.repository.PostCategoryRepository;
import com.ryu.blog.service.CategoryService;
import com.ryu.blog.vo.CategoryStatsVO;
import com.ryu.blog.vo.CategoryVO;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类服务实现类
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, allEntries = true)
    public Mono<Void> createCategory(CategoryCreateDTO categoryCreateDTO) {
        // 将DTO转换为实体
        Category category = categoryMapper.toEntity(categoryCreateDTO);
        
        return countByName(category.getName())
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new RuntimeException(MessageConstants.CATEGORY_NAME_EXISTS));
                    }
                    
                    // 设置默认值
                    category.setCreateTime(LocalDateTime.now());
                    category.setUpdateTime(LocalDateTime.now());
                    category.setIsDeleted(0);
                    
                    if (category.getSort() == null) {
                        category.setSort(0);
                    }
                    
                    return categoryRepository.save(category)
                            .doOnSuccess(savedCategory -> {
                                // 清除缓存
                                clearCategoryCache();
                            })
                            .then();
                });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, allEntries = true)
    public Mono<Void> updateCategory(CategoryUpdateDTO categoryUpdateDTO) {
        return categoryRepository.findById(categoryUpdateDTO.getId())
                .switchIfEmpty(Mono.error(new RuntimeException(MessageConstants.CATEGORY_NOT_FOUND)))
                .flatMap(existingCategory -> {
                    // 如果分类名称有变化，需要检查是否已存在
                    if (categoryUpdateDTO.getName() != null && !categoryUpdateDTO.getName().equals(existingCategory.getName())) {
                        return countByName(categoryUpdateDTO.getName())
                                .flatMap(count -> {
                                    if (count > 0) {
                                        return Mono.error(new RuntimeException(MessageConstants.CATEGORY_NAME_EXISTS));
                                    }
                                    
                                    // 使用MapStruct更新实体属性
                                    categoryMapper.updateEntityFromDTO(categoryUpdateDTO, existingCategory);
                                    existingCategory.setUpdateTime(LocalDateTime.now());
                                    
                                    return categoryRepository.save(existingCategory)
                                            .doOnSuccess(savedCategory -> clearCategoryCache())
                                            .then();
                                });
                    } else {
                        // 使用MapStruct更新实体属性
                        categoryMapper.updateEntityFromDTO(categoryUpdateDTO, existingCategory);
                        existingCategory.setUpdateTime(LocalDateTime.now());
                        
                        return categoryRepository.save(existingCategory)
                                .doOnSuccess(savedCategory -> clearCategoryCache())
                                .then();
                    }
                });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'" + CacheConstants.CATEGORY_DETAIL_KEY + "' + #id", unless = "#result == null")
    public Mono<CategoryVO> getCategoryById(Long id) {
        log.debug("从数据库获取分类详情: id={}", id);
        // 尝试从缓存获取
        String cacheKey = CacheConstants.CATEGORY_CACHE_PREFIX + "id:" + id;
        
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserializeCategoryVO)
                .switchIfEmpty(
                    categoryRepository.findById(id)
                        .switchIfEmpty(Mono.error(new RuntimeException(MessageConstants.CATEGORY_NOT_FOUND)))
                        .map(categoryMapper::toVO)
                        .flatMap(categoryVO -> 
                            // 存入缓存
                            serializeAndCache(cacheKey, categoryVO, Duration.ofMinutes(30))
                                .thenReturn(categoryVO)
                        )
                );
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = CacheConstants.CATEGORY_DETAIL_KEY + " + #id"),
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'blog:category:all'"),
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'blog:category:stats'")
    })
    public Mono<Void> deleteCategory(Long id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException(MessageConstants.CATEGORY_NOT_FOUND)))
                .flatMap(category -> {
                    // 检查是否有关联的文章
                    return categoryRepository.countArticlesByCategoryId(id)
                            .flatMap(count -> {
                                if (count > 0) {
                                    return Mono.error(new RuntimeException(MessageConstants.CATEGORY_HAS_POSTS));
                                }
                                
                                // 逻辑删除
                                category.setIsDeleted(1);
                                category.setUpdateTime(LocalDateTime.now());
                                return categoryRepository.save(category)
                                        .doOnSuccess(savedCategory -> {
                                            // 清除缓存
                                            clearCategoryCache();
                                            // 清除分类详情缓存
                                            reactiveRedisTemplate.delete(CacheConstants.CATEGORY_CACHE_PREFIX + "id:" + id)
                                                    .subscribe();
                                        })
                                        .then();
                            });
                });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'blog:category:all'", unless = "#result == null")
    public Flux<CategoryVO> getAllCategories() {
        log.debug("从数据库获取所有分类");
        // 先尝试从缓存中获取
        return reactiveRedisTemplate.opsForValue().get(CacheConstants.CATEGORY_ALL_KEY)
                .flatMapMany(jsonString -> deserializeCategoryVOList(jsonString))
                .switchIfEmpty(
                        categoryRepository.findAllCategories()
                                .map(categoryMapper::toVO)
                                .collectList()
                                .flatMap(categories -> 
                                    // 更新缓存
                                    serializeAndCache(CacheConstants.CATEGORY_ALL_KEY, categories, Duration.ofHours(1))
                                        .thenReturn(categories)
                                )
                                .flatMapMany(Flux::fromIterable)
                );
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'blog:category:stats'", unless = "#result == null")
    public Flux<CategoryStatsVO> getAllCategoriesWithArticleCount() {
        log.debug("从数据库获取所有分类统计信息");
        // 先尝试从缓存中获取
        return reactiveRedisTemplate.opsForValue().get(CacheConstants.CATEGORY_CACHE_PREFIX + "all:stats")
                .flatMapMany(jsonString -> deserializeCategoryStatsVOList(jsonString))
                .switchIfEmpty(
                        categoryRepository.findAllCategories()
                                .flatMap(category -> {
                                    return categoryRepository.countArticlesByCategoryId(category.getId())
                                            .map(count -> {
                                                category.setArticleCount(count);
                                                return category;
                                            });
                                })
                                .map(categoryMapper::toStatsVO)
                                .collectList()
                                .flatMap(categories -> 
                                    // 更新缓存
                                    serializeAndCache(
                                            CacheConstants.CATEGORY_CACHE_PREFIX + "all:stats", 
                                            categories, 
                                            Duration.ofHours(1)
                                    )
                                        .thenReturn(categories)
                                )
                                .flatMapMany(Flux::fromIterable)
                );
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'" + CacheConstants.CATEGORY_PAGE_KEY + "' + #categoryListDTO.currentPage + ':' + #categoryListDTO.pageSize + ':' + #categoryListDTO.keyword", unless = "#result == null")
    public Mono<PageResult<Category>> getCategoriesByPage(CategoryListDTO categoryListDTO) {
        // 创建分页请求
        int page = Math.max(0, categoryListDTO.getCurrentPage() - 1); // Spring Data页码从0开始
        int size = categoryListDTO.getPageSize();
        Pageable pageable = PageRequest.of(page, size);
        String keyword = categoryListDTO.getKeyword();
        
        // 查询总记录数
        return categoryRepository.countByKeyword(keyword)
                .flatMap(total -> {
                    if (total == 0) {
                        // 如果没有记录，返回空页
                        return Mono.just(new PageResult<Category>());
                    }
                    
                    // 查询分页数据
                    return categoryRepository.findByKeyword(keyword, pageable)
                            .flatMap(category -> {
                                // 查询每个分类关联的文章数量
                                return categoryRepository.countArticlesByCategoryId(category.getId())
                                        .map(count -> {
                                            category.setArticleCount(count);
                                            return category;
                                        });
                            })
                            .collectList()
                            .map(categories -> {
                                // 创建自定义分页结果
                                PageResult<Category> pageResult = new PageResult<>();
                                pageResult.setRecords(categories);
                                pageResult.setTotal(total);
                                pageResult.setSize(size);
                                pageResult.setCurrent(categoryListDTO.getCurrentPage());
                                pageResult.setPages((total + size - 1) / size); // 计算总页数
                                return pageResult;
                            });
                });
    }

    @Override
    public Mono<Boolean> checkCategoryNameExists(String name) {
        return countByName(name).map(count -> count > 0);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'" + CacheConstants.CATEGORY_ARTICLE_KEY + "' + #articleId", unless = "#result == null")
    public Flux<CategoryVO> getCategoriesByArticleId(Long articleId) {
        // 先尝试从缓存中获取
        String cacheKey = CacheConstants.CATEGORY_CACHE_PREFIX + "article:" + articleId;
        
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMapMany(jsonString -> deserializeCategoryVOList(jsonString))
                .switchIfEmpty(
                        // 从数据库中获取
                        postCategoryRepository.findByPostId(articleId)
                                .map(PostCategory::getCategoryId)
                                .flatMap(categoryRepository::findById)
                                .map(categoryMapper::toVO)
                                .collectList()
                                .flatMap(categories -> 
                                    // 更新缓存
                                    serializeAndCache(cacheKey, categories, Duration.ofHours(1))
                                        .thenReturn(categories)
                                )
                                .flatMapMany(Flux::fromIterable)
                );
    }
    
    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'" + CacheConstants.CATEGORY_ARTICLE_IDS_KEY + "' + #articleId", unless = "#result == null")
    public Flux<Long> getCategoryIdsByArticleId(Long articleId) {
        return postCategoryRepository.findByPostId(articleId)
                .map(PostCategory::getCategoryId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'" + CacheConstants.CATEGORY_ARTICLE_IDS_KEY + "' + #articleId")
    public Mono<Void> addArticleCategory(Long articleId, Long categoryId) {
        // 检查分类是否存在
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new RuntimeException(MessageConstants.CATEGORY_NOT_FOUND)))
                .flatMap(category -> {
                    // 检查关联是否已存在
                    return postCategoryRepository.countByPostIdAndCategoryId(articleId, categoryId)
                            .flatMap(count -> {
                                if (count > 0) {
                                    return Mono.empty(); // 已存在，不做处理
                                }
                                
                                // 创建关联
                                PostCategory postCategory = new PostCategory();
                                postCategory.setPostId(articleId);
                                postCategory.setCategoryId(categoryId);
                                
                                return postCategoryRepository.save(postCategory)
                                        .doOnSuccess(savedPostCategory -> {
                                            // 清除文章分类缓存
                                            clearArticleCategoriesCache(articleId);
                                        })
                                        .then();
                            });
                });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'" + CacheConstants.CATEGORY_ARTICLE_IDS_KEY + "' + #articleId")
    public Mono<Void> removeArticleCategory(Long articleId, Long categoryId) {
        return postCategoryRepository.deleteByPostIdAndCategoryId(articleId, categoryId)
                .doOnSuccess(result -> {
                    // 清除文章分类缓存
                    clearArticleCategoriesCache(articleId);
                });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE_NAME, key = "'" + CacheConstants.CATEGORY_ARTICLE_IDS_KEY + "' + #articleId")
    public Mono<Void> removeAllArticleCategories(Long articleId) {
        return postCategoryRepository.deleteByPostId(articleId)
                .doOnSuccess(result -> {
                    // 清除文章分类缓存
                    clearArticleCategoriesCache(articleId);
                });
    }
    
    // JSON序列化和缓存工具方法
    
    private <T> Mono<Boolean> serializeAndCache(String key, T value, Duration ttl) {
        try {
            String jsonString = objectMapper.writeValueAsString(value);
            return reactiveRedisTemplate.opsForValue().set(key, jsonString, ttl);
        } catch (Exception e) {
            log.error("序列化对象失败: {}", e.getMessage());
            return Mono.just(false);
        }
    }
    
    private Mono<CategoryVO> deserializeCategoryVO(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, CategoryVO.class));
        } catch (Exception e) {
            log.error("反序列化CategoryVO失败: {}", e.getMessage());
            return Mono.empty();
        }
    }
    
    private Flux<CategoryVO> deserializeCategoryVOList(String json) {
        try {
            List<CategoryVO> list = objectMapper.readValue(json, 
                    new TypeReference<List<CategoryVO>>() {});
            return Flux.fromIterable(list);
        } catch (Exception e) {
            log.error("反序列化CategoryVO列表失败: {}", e.getMessage());
            return Flux.empty();
        }
    }
    
    private Flux<CategoryStatsVO> deserializeCategoryStatsVOList(String json) {
        try {
            List<CategoryStatsVO> list = objectMapper.readValue(json, 
                    new TypeReference<List<CategoryStatsVO>>() {});
            return Flux.fromIterable(list);
        } catch (Exception e) {
            log.error("反序列化CategoryStatsVO列表失败: {}", e.getMessage());
            return Flux.empty();
        }
    }
    
    private void clearArticleCategoriesCache(Long articleId) {
        // 清除文章分类缓存
        String cacheKey = CacheConstants.CATEGORY_CACHE_PREFIX + "article:" + articleId;
        reactiveRedisTemplate.delete(cacheKey)
                .subscribe(
                        result -> log.debug("清除文章分类缓存成功: {}", articleId),
                        error -> log.error("清除文章分类缓存失败: {}", error.getMessage())
                );
    }
    
    private void clearCategoryCache() {
        // 清除分类相关缓存
        reactiveRedisTemplate.delete(CacheConstants.CATEGORY_ALL_KEY)
                .subscribe(
                        result -> log.debug("清除分类缓存成功"),
                        error -> log.error("清除分类缓存失败: {}", error.getMessage())
                );
        
        reactiveRedisTemplate.delete(CacheConstants.CATEGORY_CACHE_PREFIX + "all:stats")
                .subscribe(
                        result -> log.debug("清除分类统计缓存成功"),
                        error -> log.error("清除分类统计缓存失败: {}", error.getMessage())
                );
    }

    private Mono<Long> countByName(String name) {
        return categoryRepository.countByName(name);
    }
} 