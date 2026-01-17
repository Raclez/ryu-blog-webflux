package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostQueryDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.service.ArticleService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.PostAdminListVO;
import com.ryu.blog.vo.PostDetailVO;
import com.ryu.blog.vo.PostFrontListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 博客文章控制器
 * 负责处理文章相关的HTTP请求，包括文章的CRUD操作、状态管理和相关文章推荐等功能
 * 
 * @author ryu
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "文章管理", description = "文章相关接口")
public class PostsController {

    private final ArticleService articleService;

    /**
     * 后台管理分页查询文章列表
     * 
     * @param query 查询参数，包含分页、标题、状态、分类、标签和时间范围等条件
     * @return 包含文章列表和总数的结果
     */
    @Operation(summary = "分页查询文章列表", description = "后台管理分页查询文章列表")
    @GetMapping("/page")
    public Mono<Result<PageResult<PostAdminListVO>>> getPostsPage(
            @ParameterObject @Validated PostQueryDTO query) {
        log.info("分页查询文章列表: page={}, size={}, title={}, status={}, categoryId={}",
                query.getCurrentPage(), query.getPageSize(), query.getTitle(), query.getStatus(),
                query.getCategoryId());
        
        return articleService.getArticlePageVO(
                query.getCurrentPage() - 1, // 转为0基页码
                query.getPageSize(),
                query.getTitle(), 
                query.getStatus(),
                query.getCategoryId(),
                null, // tagId 参数不再需要
                query.getStartTime(),
                query.getEndTime()
            )
            .map(Result::success)
            .doOnSuccess(result -> log.info("分页查询文章列表成功: 总数={}", result.getData().getTotal()));
    }

    /**
     * 前台游标方式加载文章列表
     * 适用于无限滚动加载，支持向前和向后加载
     * 
     * @param cursor 游标ID，用于定位当前位置
     * @param limit 每页数量
     * @param createTime 基准创建时间
     * @param direction 加载方向，可选值：newer(较新)、older(较旧)、comprehensive(综合)
     * @return 文章列表视图对象
     */
    @Operation(summary = "前台游标加载文章列表", description = "前台游标方式加载文章列表（适用于无限滚动加载）")
    @GetMapping("/front")
    public Mono<Result<List<PostFrontListVO>>> getFrontPosts(
            @Parameter(description = "游标ID") @RequestParam(required = false) String cursor,
            @Parameter(description = "每页数量") 
            @RequestParam(defaultValue = "5") 
            @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 50, message = "每页数量不能超过50") 
            int limit,
            @Parameter(description = "基准创建时间") @RequestParam(required = false) String createTime,
            @Parameter(description = "加载方向") 
            @RequestParam(defaultValue = "comprehensive")
            @Pattern(regexp = "^(newer|older|comprehensive)$", message = "加载方向只能为newer、older或comprehensive")
            String direction) {
        log.info("前台游标加载文章列表: cursor={}, limit={}, direction={}", cursor, limit, direction);
        
        return articleService.getFrontArticlesVO(cursor, limit, createTime, direction)
                .map(voList -> {
                    log.info("前台游标加载文章列表成功: 返回记录数={}", voList.size());
                    return Result.success(voList);
                });
    }

    /**
     * 创建文章
     * 
     * @param postCreateDTO 文章创建数据传输对象
     * @return 操作结果
     */
    @Operation(summary = "创建文章", description = "创建新文章")
    @PostMapping("/save")
    public Mono<Result<Void>> createPost(@RequestBody @Validated PostCreateDTO postCreateDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("创建文章: 标题={}, 用户ID={}", postCreateDTO.getTitle(), userId);
        
        return articleService.createArticle(postCreateDTO, userId)
                .then(Mono.defer(() -> {
                    log.info("文章创建成功: 标题={}, 用户ID={}", postCreateDTO.getTitle(), userId);
                    return Mono.just(Result.<Void>success());
                }));
    }

    /**
     * 更新文章
     * 
     * @param postUpdateDTO 文章更新数据传输对象
     * @return 操作结果
     */
    @Operation(summary = "更新文章", description = "更新已有文章")
    @PutMapping("/edit")
    public Mono<Result<Void>> updatePost(@RequestBody @Validated PostUpdateDTO postUpdateDTO) {
        log.info("更新文章: ID={}, 标题={}", postUpdateDTO.getId(), postUpdateDTO.getTitle());
        
        return articleService.updateArticle(postUpdateDTO)
                .then(Mono.defer(() -> {
                    log.info("文章更新成功: ID={}", postUpdateDTO.getId());
                    return Mono.just(Result.<Void>success());
                }));
    }

    /**
     * 获取文章详情
     * 
     * @param id 文章ID
     * @return 文章详情视图对象
     */
    @Operation(summary = "获取文章详情", description = "根据ID获取文章详情")
    @GetMapping("/detail/{id}")
    public Mono<Result<PostDetailVO>> getPostDetail(
            @PathVariable 
            @Positive(message = "文章ID必须为正数") 
            Long id) {
        log.info("获取文章详情: ID={}", id);
        
        return articleService.getArticleDetailVO(id)
                .map(detailVO -> {
                    log.info("获取文章详情成功: ID={}, 标题={}", id, detailVO.getTitle());
                    return Result.success(detailVO);
                });
    }

    /**
     * 批量删除文章
     * 
     * @param ids 文章ID列表
     * @return 操作结果
     */
    @Operation(summary = "批量删除文章", description = "批量删除文章")
    @PostMapping("/delete/batch")
    public Mono<Result<Void>> batchDeletePosts(
            @RequestBody 
            @NotEmpty(message = "文章ID列表不能为空")
            @Size(max = 100, message = "单次最多删除100篇文章")
            List<String> ids) {
        log.info("批量删除文章: IDs={}", ids);
        
        return articleService.batchDeleteArticles(ids)
                .then(Mono.defer(() -> {
                    log.info("批量删除文章成功: IDs={}", ids);
                    return Mono.just(Result.<Void>success());
                }));
    }

    /**
     * 获取相关博客推荐
     * 
     * @param postId 文章ID
     * @param limit 推荐数量限制
     * @return 相关文章列表
     */
    @Operation(summary = "获取相关博客推荐", description = "获取相关博客推荐")
    @GetMapping("/related/{postId}/{limit}")
    public Mono<Result<List<PostFrontListVO>>> getRelatedPosts(
            @PathVariable 
            @Positive(message = "文章ID必须为正数") 
            Long postId,
            @PathVariable 
            @Min(value = 1, message = "推荐数量必须大于0")
            @Max(value = 20, message = "推荐数量不能超过20")
            Integer limit) {
        log.info("获取相关博客推荐: 文章ID={}, 限制数量={}", postId, limit);
        
        return articleService.getRelatedArticlesVO(postId, limit)
                .collectList()
                .map(articles -> {
                    log.info("获取相关博客推荐成功: 文章ID={}, 返回数量={}", postId, articles.size());
                    return Result.success(articles);
                });
    }
    
    /**
     * 更新文章状态
     * 使用Spring Cache注解自动管理缓存，确保数据一致性和性能最优
     * 
     * @param statusDTO 文章状态数据传输对象
     * @return 操作结果
     */
    @Operation(summary = "更新文章状态", description = "更新文章状态并自动清除相关缓存")
    @PutMapping("/status")
    public Mono<Result<Void>> updatePostStatus(@RequestBody @Validated PostStatusDTO statusDTO) {
        log.info("更新文章状态: ID={}, 状态={}", statusDTO.getId(), statusDTO.getStatus());
        
        return articleService.updateArticleStatus(statusDTO)
                .then(Mono.defer(() -> {
                    log.info("更新文章状态成功: ID={}, 状态={}", statusDTO.getId(), statusDTO.getStatus());
                    return Mono.just(Result.<Void>success());
                }));
    }

    /**
     * 导入Markdown文件创建文章
     * 
     * @param file 上传的Markdown文件
     * @param categoryId 分类ID
     * @return 操作结果
     */
    @Operation(summary = "导入Markdown文件", description = "导入Markdown文件创建文章")
    @PostMapping(value = "/upload-md", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Result<Void>> importMarkdown(
            @RequestPart("files") FilePart file,
            @RequestParam(required = false) 
            @Positive(message = "分类ID必须为正数")
            Long categoryId) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("导入Markdown文件: 文件名={}, 分类ID={}, 用户ID={}", file.filename(), categoryId, userId);
        
        return articleService.importMarkdownArticle(file, categoryId, userId)
                .then(Mono.defer(() -> {
                    log.info("Markdown文件导入成功: 文件名={}, 用户ID={}", file.filename(), userId);
                    return Mono.just(Result.<Void>success());
                }));
    }

    /**
     * 将文章导出为Markdown文件
     * 
     * @param id 文章ID
     * @return Markdown文件下载
     */
    @Operation(summary = "导出为Markdown文件", description = "将文章导出为Markdown文件")
    @GetMapping("/export/{id}")
    public Mono<ResponseEntity<ByteArrayResource>> exportMarkdown(
            @PathVariable 
            @Positive(message = "文章ID必须为正数") 
            Long id) {
        log.info("导出文章为Markdown: ID={}", id);
        
        return articleService.exportArticleToMarkdown(id)
                .map(result -> {
                    String filename = result.getFilename();
                    byte[] content = result.getContent().getBytes(StandardCharsets.UTF_8);
                    
                    ByteArrayResource resource = new ByteArrayResource(content);
                    
                    log.info("文章导出为Markdown成功: ID={}, 文件名={}", id, filename);

                    // 对文件名进行URL编码，以支持中文文件名
                    String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                            .replaceAll("\\+", "%20"); // 将空格的+替换为%20

                    // 设置两种Content-Disposition，兼容不同的浏览器
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                            .contentType(MediaType.TEXT_MARKDOWN)
                            .contentLength(content.length)
                            .body(resource);
                });
    }
}
