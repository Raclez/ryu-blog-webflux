package com.ryu.blog.service;

import com.ryu.blog.dto.CategoryCreateDTO;
import com.ryu.blog.dto.CategoryListDTO;
import com.ryu.blog.dto.CategoryUpdateDTO;
import com.ryu.blog.entity.Category;
import com.ryu.blog.vo.CategoryStatsVO;
import com.ryu.blog.vo.CategoryVO;
import com.ryu.blog.vo.PageResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 分类服务接口
 * @author ryu
 */
public interface CategoryService {

    /**
     * 创建分类
     * @param categoryCreateDTO 分类创建DTO
     * @return 创建结果
     */
    Mono<Void> createCategory(CategoryCreateDTO categoryCreateDTO);

    /**
     * 更新分类
     * @param categoryUpdateDTO 分类更新DTO
     * @return 更新结果
     */
    Mono<Void> updateCategory(CategoryUpdateDTO categoryUpdateDTO);

    /**
     * 根据ID获取分类
     * @param id 分类ID
     * @return 分类VO
     */
    Mono<CategoryVO> getCategoryById(Long id);

    /**
     * 删除分类
     * @param id 分类ID
     * @return 删除结果
     */
    Mono<Void> deleteCategory(Long id);

    /**
     * 获取所有分类
     * @return 分类VO列表
     */
    Flux<CategoryVO> getAllCategories();

    /**
     * 获取所有分类（包含文章数量）
     * @return 分类统计VO列表
     */
    Flux<CategoryStatsVO> getAllCategoriesWithArticleCount();

    /**
     * 分页查询分类
     * @param categoryListDTO 分类列表查询DTO
     * @return 分页结果
     */
    Mono<PageResult<Category>> getCategoriesByPage(CategoryListDTO categoryListDTO);

    /**
     * 检查分类名称是否存在
     * @param name 分类名称
     * @return 是否存在
     */
    Mono<Boolean> checkCategoryNameExists(String name);
    
    /**
     * 根据文章ID获取分类
     * @param articleId 文章ID
     * @return 分类VO列表
     */
    Flux<CategoryVO> getCategoriesByArticleId(Long articleId);
    
    /**
     * 获取文章的分类ID列表
     * @param articleId 文章ID
     * @return 分类ID列表
     */
    Flux<Long> getCategoryIdsByArticleId(Long articleId);
    
    /**
     * 添加文章分类关联
     * @param articleId 文章ID
     * @param categoryId 分类ID
     * @return 操作结果
     */
    Mono<Void> addArticleCategory(Long articleId, Long categoryId);
    
    /**
     * 删除文章分类关联
     * @param articleId 文章ID
     * @param categoryId 分类ID
     * @return 操作结果
     */
    Mono<Void> removeArticleCategory(Long articleId, Long categoryId);
    
    /**
     * 删除文章的所有分类关联
     * @param articleId 文章ID
     * @return 操作结果
     */
    Mono<Void> removeAllArticleCategories(Long articleId);
} 