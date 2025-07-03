package com.ryu.blog.service;


import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.PostCategory;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.vo.MarkdownExportVO;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.PostAdminListVO;
import com.ryu.blog.vo.PostDetailVO;
import com.ryu.blog.vo.PostFrontListVO;
import org.springframework.http.codec.multipart.FilePart;
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
     * 使用DTO创建文章
     * 使用Spring Cache注解自动清除前台、热门文章和后台列表缓存
     * 
     * @param articleCreateDTO 文章创建DTO
     * @param userId 用户ID
     * @return 创建结果
     */
    Mono<Posts> createArticle(PostCreateDTO articleCreateDTO, Long userId);

    
    /**
     * 使用DTO更新文章
     * 使用Spring Cache注解自动管理缓存，会清除以下缓存：
     * 1. 文章详情缓存
     * 2. 前台文章列表缓存
     * 3. 热门文章缓存
     * 4. 相关文章推荐缓存
     * 5. 后台文章列表缓存
     * 
     * @param articleUpdateDTO 文章更新DTO
     * @return 更新结果
     */
    Mono<Posts> updateArticle(PostUpdateDTO articleUpdateDTO);
    
    /**
     * 更新文章状态
     * 使用Spring Cache注解自动管理缓存，会清除以下缓存：
     * 1. 文章详情缓存
     * 2. 前台文章列表缓存
     * 3. 热门文章缓存
     * 4. 相关文章推荐缓存
     * 5. 后台文章列表缓存
     * 
     * @param statusDTO 文章状态DTO
     * @return 更新结果
     */
    Mono<Posts> updateArticleStatus(PostStatusDTO statusDTO);

    
    /**
     * 根据ID获取文章详情视图对象
     * @param id 文章ID
     * @return 文章详情视图对象
     */
    Mono<PostDetailVO> getArticleDetailVO(Long id);

    /**
     * 删除文章
     * 使用Spring Cache注解自动清除相关缓存
     * 
     * @param id 文章ID
     * @return 删除结果
     */
    Mono<Void> deleteArticle(Long id);

    /**
     * 批量删除文章
     * 使用Spring Cache注解自动清除所有相关缓存
     * 
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
     * 分页条件查询文章列表并返回PostAdminListVO类型的PageResult
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
    Mono<PageResult<PostAdminListVO>> getArticlePageVO(int page, int size, String title, Integer status, Long categoryId, Long tagId, String startTime, String endTime);

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
     * 增加文章点赞数
     * 使用Spring Cache注解自动清除文章详情、热门文章和前台列表缓存
     * 
     * @param id 文章ID
     * @return 更新结果
     */
    Mono<Integer> incrementLikes(Long id);

    /**
     * 增加文章评论数
     * 使用Spring Cache注解自动清除文章详情、热门文章和前台列表缓存
     * 
     * @param id 文章ID
     * @return 更新结果
     */
    Mono<Integer> incrementComments(Long id);

    /**
     * 减少文章评论数
     * 使用Spring Cache注解自动清除文章详情、热门文章和前台列表缓存
     * 
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
     * 
     * @param articleId 文章ID
     * @param categoryId 分类ID
     * @return 关联结果
     */
    Mono<PostCategory> addArticleCategory(Long articleId, Long categoryId);
    
    /**
     * 删除文章分类关联
     * 
     * @param articleId 文章ID
     * @param categoryId 分类ID
     * @return 删除结果
     */
    Mono<Void> removeArticleCategory(Long articleId, Long categoryId);
    
    /**
     * 删除文章的所有分类关联
     * 
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

    /**
     * 前台游标方式加载文章列表（直接返回VO列表）
     * @param cursor 游标ID
     * @param limit 每页大小
     * @param createTime 基准创建时间
     * @param direction 加载方向
     * @return 文章VO列表
     */
    Mono<List<PostFrontListVO>> getFrontArticlesVO(String cursor, int limit, String createTime, String direction);

    /**
     * 导入Markdown文件创建文章
     * 使用Spring Cache注解自动清除前台、热门文章和后台列表缓存
     * 
     * @param file Markdown文件
     * @param categoryId 分类ID
     * @param userId 用户ID
     * @return 操作结果
     */
    Mono<Void> importMarkdownArticle(FilePart file, Long categoryId, Long userId);
    
    /**
     * 将文章导出为Markdown文件
     * @param id 文章ID
     * @return Markdown内容和文件名
     */
    Mono<MarkdownExportVO> exportArticleToMarkdown(Long id);
} 