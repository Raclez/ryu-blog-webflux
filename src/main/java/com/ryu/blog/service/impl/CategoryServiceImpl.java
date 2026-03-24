package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.CategoryCreateDTO;
import com.ryu.blog.dto.CategoryListDTO;
import com.ryu.blog.dto.CategoryUpdateDTO;
import com.ryu.blog.entity.Category;
import com.ryu.blog.entity.PostCategory;
import com.ryu.blog.exception.BusinessException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, allEntries = true)
    public Mono<Void> createCategory(CategoryCreateDTO categoryCreateDTO) {
        Category category = categoryMapper.toEntity(categoryCreateDTO);
        
        return checkCategoryNameExists(category.getName())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(BusinessException.categoryNameExists());
                    }
                    
                    // 设置默认值
                    category.setCreateTime(LocalDateTime.now());
                    category.setUpdateTime(LocalDateTime.now());
                    category.setIsDeleted(false);
                    
                    if (category.getSort() == null) {
                        category.setSort(0);
                    }
                    
                    return categoryRepository.save(category).then();
                });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, allEntries = true)
    public Mono<Void> updateCategory(CategoryUpdateDTO categoryUpdateDTO) {
        return categoryRepository.findById(categoryUpdateDTO.getId())
                .switchIfEmpty(Mono.error(BusinessException.categoryNotFound()))
                .flatMap(existingCategory -> {
                    // 如果分类名称有变化，需要检查是否已存在
                    if (categoryUpdateDTO.getName() != null && !categoryUpdateDTO.getName().equals(existingCategory.getName())) {
                        return checkCategoryNameExists(categoryUpdateDTO.getName())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Mono.error(BusinessException.categoryNameExists());
                                    }
                                    return processCategoryUpdate(categoryUpdateDTO, existingCategory);
                                });
                    } else {
                        return processCategoryUpdate(categoryUpdateDTO, existingCategory);
                    }
                });
    }
    
    /**
     * 处理分类更新
     */
    private Mono<Void> processCategoryUpdate(CategoryUpdateDTO categoryUpdateDTO, Category existingCategory) {
        categoryMapper.updateEntityFromDTO(categoryUpdateDTO, existingCategory);
        existingCategory.setUpdateTime(LocalDateTime.now());
        return categoryRepository.save(existingCategory).then();
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_DETAIL_KEY + "' + #id")
    public Mono<CategoryVO> getCategoryById(Long id) {
        log.debug("从数据库获取分类详情: id={}", id);
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.categoryNotFound()))
                .map(categoryMapper::toVO);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_DETAIL_KEY + "' + #id"),
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'allCategories'"),
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'categoryStats'")
    })
    public Mono<Void> deleteCategory(Long id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.categoryNotFound()))
                .flatMap(category -> 
                    // 检查是否有关联的文章
                    categoryRepository.countArticlesByCategoryId(id)
                            .flatMap(count -> {
                                if (count > 0) {
                                    return Mono.error(BusinessException.categoryHasPosts());
                                }
                                
                                // 逻辑删除
                                category.setIsDeleted(true);
                                category.setUpdateTime(LocalDateTime.now());
                                return categoryRepository.save(category).then();
                            })
                );
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'allCategories'")
    public Flux<CategoryVO> getAllCategories() {
        log.debug("从数据库获取所有分类");
        return categoryRepository.findAllCategories()
                .map(categoryMapper::toVO);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'categoryStats'")
    public Flux<CategoryStatsVO> getAllCategoriesWithArticleCount() {
        log.debug("从数据库获取所有分类统计信息");
        return categoryRepository.findAllCategories()
                .flatMap(category -> 
                    categoryRepository.countArticlesByCategoryId(category.getId())
                            .map(count -> {
                                category.setArticleCount(count);
                                return category;
                            })
                )
                .map(categoryMapper::toStatsVO);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE, 
               key = "'" + CacheConstants.CATEGORY_PAGE_KEY + "' + #categoryListDTO.currentPage + ':' + #categoryListDTO.pageSize + ':' + #categoryListDTO.keyword")
    public Mono<PageResult<CategoryVO>> getCategoriesByPage(CategoryListDTO categoryListDTO) {
        int page = Math.max(0, categoryListDTO.getCurrentPage() - 1);
        int size = categoryListDTO.getPageSize();
        Pageable pageable = PageRequest.of(page, size);
        String keyword = categoryListDTO.getKeyword();
        
        log.debug("分页查询分类, 当前页: {}, 每页条数: {}, 关键字: {}", 
                 categoryListDTO.getCurrentPage(), size, keyword);
        
        return categoryRepository.countByKeyword(keyword)
                .flatMap(total -> {
                    if (total == 0) {
                        log.debug("未找到匹配的分类记录");
                        return Mono.just(new PageResult<CategoryVO>());
                    }
                    
                    return categoryRepository.findByKeyword(keyword, pageable)
                            .map(categoryMapper::toVO)
                            .collectList()
                            .map(categories -> {
                                PageResult<CategoryVO> pageResult = new PageResult<>();
                                pageResult.setRecords(categories);
                                pageResult.setTotal(total);
                                pageResult.setSize(size);
                                pageResult.setCurrent(categoryListDTO.getCurrentPage());
                                pageResult.setPages((total + size - 1) / size);
                                return pageResult;
                            });
                })
                .doOnSuccess(result -> log.debug("分页查询分类成功，总数: {}", result.getTotal()))
                .doOnError(e -> log.error("分页查询分类失败: {}", e.getMessage(), e));
    }

    @Override
    public Mono<Boolean> checkCategoryNameExists(String name) {
        return categoryRepository.countByName(name)
                .map(count -> count > 0);
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_ARTICLE_KEY + "' + #articleId")
    public Flux<CategoryVO> getCategoriesByArticleId(Long articleId) {
        return postCategoryRepository.findByPostId(articleId)
                .map(PostCategory::getCategoryId)
                .flatMap(categoryRepository::findById)
                .map(categoryMapper::toVO);
    }
    
    @Override
    @Cacheable(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_ARTICLE_IDS_KEY + "' + #articleId")
    public Flux<Long> getCategoryIdsByArticleId(Long articleId) {
        return postCategoryRepository.findByPostId(articleId)
                .map(PostCategory::getCategoryId);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_ARTICLE_IDS_KEY + "' + #articleId"),
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_ARTICLE_KEY + "' + #articleId")
    })
    public Mono<Void> addArticleCategory(Long articleId, Long categoryId) {
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(BusinessException.categoryNotFound()))
                .flatMap(category -> 
                    postCategoryRepository.countByPostIdAndCategoryId(articleId, categoryId)
                            .flatMap(count -> {
                                if (count > 0) {
                                    return Mono.empty();
                                }
                                
                                PostCategory postCategory = new PostCategory();
                                postCategory.setPostId(articleId);
                                postCategory.setCategoryId(categoryId);
                                
                                return postCategoryRepository.save(postCategory).then();
                            })
                );
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_ARTICLE_IDS_KEY + "' + #articleId"),
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_ARTICLE_KEY + "' + #articleId")
    })
    public Mono<Void> removeArticleCategory(Long articleId, Long categoryId) {
        return postCategoryRepository.deleteByPostIdAndCategoryId(articleId, categoryId);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_ARTICLE_IDS_KEY + "' + #articleId"),
        @CacheEvict(cacheNames = CacheConstants.CATEGORY_CACHE, key = "'" + CacheConstants.CATEGORY_ARTICLE_KEY + "' + #articleId")
    })
    public Mono<Void> removeAllArticleCategories(Long articleId) {
        return postCategoryRepository.deleteByPostId(articleId);
    }
} 