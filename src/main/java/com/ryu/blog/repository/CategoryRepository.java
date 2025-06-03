package com.ryu.blog.repository;

import com.ryu.blog.entity.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 分类存储库接口
 * @author ryu
 */
@Repository
public interface CategoryRepository extends R2dbcRepository<Category, Long> {

    /**
     * 查询所有未删除的分类
     * @return 分类列表
     */
    @Query("SELECT * FROM t_categories WHERE is_deleted = 0 ORDER BY sort ASC")
    Flux<Category> findAllCategories();

    /**
     * 检查分类名称是否存在
     * @param name 分类名称
     * @return 匹配记录的数量
     */
    @Query("SELECT COUNT(1) FROM t_categories WHERE name = :name AND is_deleted = 0")
    Mono<Long> countByName(String name);

    /**
     * 获取分类的文章数量
     * @param categoryId 分类ID
     * @return 文章数量
     */
    @Query("SELECT COUNT(1) AS count FROM t_post_categories WHERE category_id = :categoryId")
    Mono<Long> countArticlesByCategoryId(Long categoryId);
    
    /**
     * 根据关键字查询分类
     * @param keyword 关键字
     * @param pageable 分页参数
     * @return 分类列表
     */
    @Query("SELECT * FROM t_categories WHERE is_deleted = 0 " +
           "AND (COALESCE(:keyword, '') = '' OR name LIKE CONCAT('%', :keyword, '%') " +
           "OR description LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY sort ASC")
    Flux<Category> findByKeyword(String keyword, Pageable pageable);
    
    /**
     * 统计符合条件的分类总数
     * @param keyword 关键字
     * @return 分类总数
     */
    @Query("SELECT COUNT(1) FROM t_categories WHERE is_deleted = 0 " +
           "AND (COALESCE(:keyword, '') = '' OR name LIKE CONCAT('%', :keyword, '%') " +
           "OR description LIKE CONCAT('%', :keyword, '%'))")
    Mono<Long> countByKeyword(String keyword);
} 