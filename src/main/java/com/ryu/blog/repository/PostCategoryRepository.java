package com.ryu.blog.repository;

import com.ryu.blog.entity.PostCategory;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文章分类关联存储库
 * @author ryu 475118582@qq.com
 */
@Repository
public interface PostCategoryRepository extends R2dbcRepository<PostCategory, Long> {
    
    /**
     * 根据文章ID查找关联
     * @param postId 文章ID
     * @return 关联列表
     */
    Flux<PostCategory> findByPostId(Long postId);
    
    /**
     * 根据分类ID查找关联
     * @param categoryId 分类ID
     * @return 关联列表
     */
    Flux<PostCategory> findByCategoryId(Long categoryId);
    
    /**
     * 根据文章ID删除关联
     * @param postId 文章ID
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_post_categories WHERE post_id = :postId")
    Mono<Void> deleteByPostId(Long postId);
    
    /**
     * 根据分类ID删除关联
     * @param categoryId 分类ID
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_post_categories WHERE category_id = :categoryId")
    Mono<Void> deleteByCategoryId(Long categoryId);
    
    /**
     * 根据文章ID和分类ID查找关联
     * @param postId 文章ID
     * @param categoryId 分类ID
     * @return 关联对象
     */
    Mono<PostCategory> findByPostIdAndCategoryId(Long postId, Long categoryId);
    
    /**
     * 检查文章和分类的关联是否存在
     * @param postId 文章ID
     * @param categoryId 分类ID
     * @return 匹配记录的数量
     */
    @Query("SELECT COUNT(1) FROM t_post_categories WHERE post_id = :postId AND category_id = :categoryId")
    Mono<Long> countByPostIdAndCategoryId(Long postId, Long categoryId);
    
    /**
     * 根据文章ID和分类ID删除关联
     * @param postId 文章ID
     * @param categoryId 分类ID
     * @return 删除结果
     */
    @Modifying
    @Query("DELETE FROM t_post_categories WHERE post_id = :postId AND category_id = :categoryId")
    Mono<Void> deleteByPostIdAndCategoryId(Long postId, Long categoryId);
} 