package com.ryu.blog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostQueryDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostStickyDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.enums.PublishMode;
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
 * <p><b>鉴权模型</b>：
 * <ul>
 *   <li><b>能力校验</b>（{@code @SaCheckPermission}）—— 「有没有资格管理文章」，
 *       对应 {@code content:post:*}；</li>
 *   <li><b>归属校验</b>（{@code OwnershipPolicy}）—— 「有没有资格改这一篇」，
 *       由 Service 层用 {@code OperatorContext}（从 Reactor Context 取）判定。</li>
 * </ul>
 * 两者是<b>正交</b>的：管理员有能力，但默认也只能改自己的文章（除非持有
 * {@code system:user:manage}）；普通读者有能力也不能改别人的。
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
    @SaCheckPermission(PermissionConstants.POST_VIEW)
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
    @Operation(summary = "创建文章", description = "创建新文章（支持存草稿 / 立即发布 / 定时发布）")
    @PostMapping("/save")
    @SaCheckPermission(PermissionConstants.POST_ADD)
    public Mono<Result<Void>> createPost(@RequestBody @Validated PostCreateDTO postCreateDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("创建文章: 标题={}, 用户ID={}, 发布方式={}",
                postCreateDTO.getTitle(), userId, postCreateDTO.getPublishMode());

        // 发布方式要动态校验：注解上的 @SaCheckPermission 是静态的，
        // 而「存草稿」只需要 content:post:add，「立即发布 / 定时发布」还需要
        // content:post:publish —— 不校验就等于给发布闸门开了个后门
        // （当初 DTO 里的 status 字段就是这么被绕过去的）。
        checkPublishCapability(postCreateDTO.getPublishMode());

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
    @Operation(summary = "更新文章", description = "更新已有文章（可改发布方式：存草稿 / 立即发布 / 定时发布）")
    @PutMapping("/edit")
    @SaCheckPermission(PermissionConstants.POST_EDIT)
    public Mono<Result<Void>> updatePost(@RequestBody @Validated PostUpdateDTO postUpdateDTO) {
        log.info("更新文章: ID={}, 标题={}, 发布方式={}",
                postUpdateDTO.getId(), postUpdateDTO.getTitle(), postUpdateDTO.getPublishMode());

        // 同 createPost：只有请求里明确要求「发布」时才额外要 content:post:publish。
        // publishMode 为 null 表示本次不改状态（普通编辑），不该被拦。
        checkPublishCapability(postUpdateDTO.getPublishMode());

        return articleService.updateArticle(postUpdateDTO)
                .then(Mono.defer(() -> {
                    log.info("文章更新成功: ID={}", postUpdateDTO.getId());
                    return Mono.just(Result.<Void>success());
                }));
    }

    /**
     * 取消定时发布
     *
     * <p>文章从「待发布」退回「草稿」，对应的定时发布任务一并撤销。
     *
     * @param id 文章ID
     * @return 操作结果
     */
    @Operation(summary = "取消定时发布", description = "文章退回草稿，并撤销对应的定时发布任务")
    @PostMapping("/{id}/publish/cancel")
    @SaCheckPermission(PermissionConstants.POST_UNPUBLISH)
    public Mono<Result<Void>> cancelScheduledPublish(
            @PathVariable
            @Positive(message = "文章ID必须为正数")
            Long id) {
        log.info("取消定时发布: ID={}", id);

        return articleService.cancelScheduledPublish(id)
                .then(Mono.defer(() -> {
                    log.info("取消定时发布成功: ID={}", id);
                    return Mono.just(Result.<Void>success());
                }));
    }

    /**
     * 设置/取消文章置顶
     *
     * <p>列表页上的「置顶」开关直接打这个端点 ——
     * 置顶是独立于正文编辑的操作，不该要求作者把整篇文章重新提交一遍。
     *
     * @param stickyDTO 文章ID与目标置顶状态
     * @return 操作结果
     */
    @Operation(summary = "设置文章置顶", description = "设置或取消文章的置顶状态（列表页一键切换）")
    @PutMapping("/sticky")
    @SaCheckPermission(PermissionConstants.POST_EDIT)
    public Mono<Result<Void>> updatePostSticky(@RequestBody @Validated PostStickyDTO stickyDTO) {
        log.info("设置文章置顶: ID={}, 置顶={}", stickyDTO.getId(), stickyDTO.getIsSticky());

        return articleService.updateSticky(stickyDTO)
                .then(Mono.defer(() -> {
                    log.info("文章置顶状态更新成功: ID={}", stickyDTO.getId());
                    return Mono.just(Result.<Void>success());
                }));
    }

    /**
     * 按发布方式动态校验「发布」能力
     *
     * <p>{@code DRAFT}（含未传）只写草稿，不需要发布能力；
     * {@code IMMEDIATE} / {@code SCHEDULED} 会让文章对外可见或即将对外可见，
     * 必须持有 {@code content:post:publish}。
     *
     * <p>校验在<b>方法体内同步</b>完成：注解切面 {@code SaPermissionAspect}
     * 读的是静态注解，动态条件只能自己调。抛出的 {@code NotPermissionException}
     * 由 {@code GlobalExceptionHandler} 统一映射成 403。
     */
    private void checkPublishCapability(PublishMode mode) {
        if (mode == null || mode == PublishMode.DRAFT) {
            return;
        }
        StpUtil.checkPermission(PermissionConstants.POST_PUBLISH);
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
                    // 不把标题（用户创作内容）写进日志
                    log.info("获取文章详情成功: ID={}", id);
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
    @SaCheckPermission(PermissionConstants.POST_REMOVE)
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
    // 发布与取消发布是同一个端点的两个方向，Sa-Token 的多值权限默认「满足其一即可」
    @SaCheckPermission({PermissionConstants.POST_PUBLISH, PermissionConstants.POST_UNPUBLISH})
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
    @SaCheckPermission(PermissionConstants.POST_ADD)
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
    // 能力校验（能管理文章）+ 归属校验（Service 层：只能导出自己的）
    @SaCheckPermission(PermissionConstants.POST_VIEW)
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
