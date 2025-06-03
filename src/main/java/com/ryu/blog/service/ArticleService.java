package com.ryu.blog.service;


import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.PostCategory;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.vo.PostDetailVO;
import com.ryu.blog.vo.PostFrontListVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 文章服务接口
 * @author ryu
 */
public interface ArticleService {

    /**
     * 创建文章
     * @param article 文章信息
     * @return 创建结果
     */
    Mono<Posts> createArticle(Posts article);
    
    /**
     * 使用DTO创建文章
     * @param articleCreateDTO 文章创建DTO
     * @param userId 用户ID
     * @return 创建结果
     */
    Mono<Posts> createArticle(PostCreateDTO articleCreateDTO, Long userId);

    /**
     * 更新文章
     * @param article 文章信息
     * @return 更新结果
     */
    Mono<Posts> updateArticle(Posts article);
    
    /**
     * 使用DTO更新文章
     * @param articleUpdateDTO 文章更新DTO
     * @return 更新结果
     */
    Mono<Posts> updateArticle(PostUpdateDTO articleUpdateDTO);
    
    /**
     * 更新文章状态
     * @param statusDTO 文章状态DTO
     * @return 更新结果
     */
    Mono<Posts> updateArticleStatus(PostStatusDTO statusDTO);

    /**
     * 根据ID获取文章
     * @param id 文章ID
     * @return 文章信息
     */
    Mono<Posts> getArticleById(Long id);
    
    /**
     * 根据ID获取文章详情视图对象
     * @param id 文章ID
     * @return 文章详情视图对象
     */
    Mono<PostDetailVO> getArticleDetailVO(Long id);

    /**
     * 删除文章
     * @param id 文章ID
     * @return 删除结果
     */
    Mono<Void> deleteArticle(Long id);

    /**
     * 批量删除文章
     * @param ids 文章ID列表
     * @return 删除结果
     */
    Mono<Void> batchDeleteArticles(List<String> ids);

    /**
     * 获取已发布的文章列表
     * @param page 页码
     * @param size 每页大小
     * @return 文章列表
     */
    Flux<Posts> getPublishedArticles(int page, int size);

    /**
     * 获取已发布的文章总数
     * @return 文章总数
     */
    Mono<Long> countPublishedArticles();

    /**
     * 分页条件查询文章列表
     * @param page 页码
     * @param size 每页大小
     * @param title 文章标题
     * @param status 文章状态
     * @param categoryId 分类ID
     * @param tagId 标签ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 文章分页数据
     */
    Mono<Map<String, Object>> getArticlePage(int page, int size, String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime);

    /**
     * 前台游标方式加载文章列表
     * @param cursor 游标ID
     * @param limit 每页大小
     * @param createTime 基准创建时间
     * @param direction 加载方向
     * @return 文章列表和分页信息
     */
    Mono<Map<String, Object>> getFrontArticles(String cursor, int limit, String createTime, String direction);

    /**
     * 获取相关博客推荐
     * @param postId 文章ID
     * @param limit 推荐数量
     * @return 相关文章列表
     */
    Flux<Posts> getRelatedArticles(Long postId, Integer limit);
    
    /**
     * 获取相关博客推荐（VO）
     * @param postId 文章ID
     * @param limit 推荐数量
     * @return 相关文章VO列表
     */
    Flux<PostFrontListVO> getRelatedArticlesVO(Long postId, Integer limit);

    /**
     * 根据分类ID获取文章列表
     * @param categoryId 分类ID
     * @param page 页码
     * @param size 每页大小
     * @return 文章列表
     */
    Flux<Posts> getArticlesByCategoryId(Long categoryId, int page, int size);

    /**
     * 根据分类ID获取文章总数
     * @param categoryId 分类ID
     * @return 文章总数
     */
    Mono<Long> countArticlesByCategoryId(Long categoryId);

    /**
     * 根据用户ID获取文章列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 文章列表
     */
    Flux<Posts> getArticlesByUserId(Long userId, int page, int size);

    /**
     * 根据用户ID获取文章总数
     * @param userId 用户ID
     * @return 文章总数
     */
    Mono<Long> countArticlesByUserId(Long userId);

    /**
     * 增加文章浏览量
     * @param id 文章ID
     * @return 更新结果
     */
    Mono<Integer> incrementViews(Long id);

    /**
     * 增加文章点赞数
     * @param id 文章ID
     * @return 更新结果
     */
    Mono<Integer> incrementLikes(Long id);

    /**
     * 增加文章评论数
     * @param id 文章ID
     * @return 更新结果
     */
    Mono<Integer> incrementComments(Long id);

    /**
     * 减少文章评论数
     * @param id 文章ID
     * @return 更新结果
     */
    Mono<Integer> decrementComments(Long id);

    /**
     * 获取热门文章
     * @param limit 限制数量
     * @return 文章列表
     */
    Flux<Posts> getHotArticles(int limit);
    
    /**
     * 添加文章分类关联
     * @param articleId 文章ID
     * @param categoryId 分类ID
     * @return 关联结果
     */
    Mono<PostCategory> addArticleCategory(Long articleId, Long categoryId);
    
    /**
     * 删除文章分类关联
     * @param articleId 文章ID
     * @param categoryId 分类ID
     * @return 删除结果
     */
    Mono<Void> removeArticleCategory(Long articleId, Long categoryId);
    
    /**
     * 删除文章的所有分类关联
     * @param articleId 文章ID
     * @return 删除结果
     */
    Mono<Void> removeAllArticleCategories(Long articleId);
    
    /**
     * 获取文章的所有分类ID
     * @param articleId 文章ID
     * @return 分类ID列表
     */
    Flux<Long> getArticleCategoryIds(Long articleId);
} 