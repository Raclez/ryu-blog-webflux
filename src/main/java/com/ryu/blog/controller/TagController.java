package com.ryu.blog.controller;

import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.dto.TagCreateDTO;
import com.ryu.blog.dto.TagListDTO;
import com.ryu.blog.dto.TagUpdateDTO;
import com.ryu.blog.service.TagService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 标签控制器
 * <p>
 * 提供标签管理的RESTful API接口，包括：
 * <ul>
 *   <li>标签的CRUD操作（创建、查询、更新、删除）</li>
 *   <li>标签与文章的关联管理</li>
 *   <li>标签列表查询（分页、热门标签）</li>
 *   <li>标签名称唯一性校验</li>
 * </ul>
 * 
 * <p>所有接口均采用响应式编程模型，返回Mono类型的响应式流
 * 
 * @author ryu
 * @since 1.0
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理", description = "标签相关接口")
public class TagController {

    private final TagService tagService;

    /**
     * 创建标签
     * <p>
     * 创建新的标签，标签名称必须唯一。创建成功后会清除相关缓存。
     * 
     * @param tagCreateDTO 标签创建DTO，包含标签名称、描述等信息
     * @return 创建结果，包含成功消息
     * @throws com.ryu.blog.exception.BusinessException 当标签名称已存在时抛出
     */
    @Operation(summary = "创建标签", description = "创建新标签，标签名称必须唯一")
    @PostMapping("/save")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Result<String>> createTag(@RequestBody @Validated TagCreateDTO tagCreateDTO) {
        log.info("[标签创建] 开始处理创建标签请求 - 标签名称: {}", tagCreateDTO.getName());
        
        return tagService.createTag(tagCreateDTO)
                .doOnSuccess(success -> log.info("[标签创建] 标签创建成功 - 标签名称: {}", tagCreateDTO.getName()))
                .map(success -> Result.success(MessageConstants.TAG_CREATE_SUCCESS))
                .doOnError(e -> log.error("[标签创建] 标签创建失败 - 标签名称: {}, 错误: {}", 
                        tagCreateDTO.getName(), e.getMessage(), e))
                .doFinally(signalType -> log.debug("[标签创建] 请求处理完成 - 信号类型: {}", signalType));
    }

    /**
     * 更新标签
     * <p>
     * 更新已存在的标签信息。如果修改标签名称，会检查新名称是否已被使用。
     * 更新成功后会清除相关缓存。
     * 
     * @param tagUpdateDTO 标签更新DTO，包含标签ID和需要更新的字段
     * @return 更新结果，包含成功消息
     * @throws com.ryu.blog.exception.BusinessException 当标签不存在或新名称已被使用时抛出
     */
    @Operation(summary = "更新标签", description = "更新已有标签信息，支持部分字段更新")
    @PutMapping("/edit")
    public Mono<Result<String>> updateTag(@RequestBody @Validated TagUpdateDTO tagUpdateDTO) {
        log.info("[标签更新] 开始处理更新标签请求 - 标签ID: {}, 新名称: {}", 
                tagUpdateDTO.getId(), tagUpdateDTO.getName());
        
        return tagService.updateTag(tagUpdateDTO)
                .doOnSuccess(success -> log.info("[标签更新] 标签更新成功 - 标签ID: {}", tagUpdateDTO.getId()))
                .map(success -> Result.success(MessageConstants.TAG_UPDATE_SUCCESS))
                .doOnError(e -> log.error("[标签更新] 标签更新失败 - 标签ID: {}, 错误: {}", 
                        tagUpdateDTO.getId(), e.getMessage(), e));
    }

    /**
     * 获取标签详情
     * <p>
     * 根据标签ID查询标签的详细信息。结果会被缓存以提高查询性能。
     * 
     * @param id 标签ID，必须为正整数
     * @return 标签详情VO对象
     * @throws com.ryu.blog.exception.BusinessException 当标签不存在时抛出
     */
    @Operation(summary = "获取标签详情", description = "根据ID获取标签详细信息")
    @GetMapping("/detail/{id}")
    public Mono<Result<TagVO>> getTagById(
            @Parameter(description = "标签ID", required = true) 
            @PathVariable @NotNull @Positive Long id) {
        log.info("[标签查询] 查询标签详情 - 标签ID: {}", id);
        
        return tagService.getTagById(id)
                .doOnSuccess(tag -> log.debug("[标签查询] 标签详情查询成功 - 标签ID: {}, 标签名称: {}", 
                        id, tag.getName()))
                .map(Result::success);
    }

    /**
     * 删除标签
     * <p>
     * 逻辑删除指定的标签。如果标签关联了文章，会先删除关联关系再删除标签。
     * 删除成功后会清除相关缓存。
     * 
     * @param id 标签ID，必须为正整数
     * @return 删除结果，包含成功消息
     * @throws com.ryu.blog.exception.BusinessException 当标签不存在时抛出
     */
    @Operation(summary = "删除标签", description = "逻辑删除标签，会同时删除与文章的关联关系")
    @DeleteMapping("/delete/{id}")
    public Mono<Result<String>> deleteTag(
            @Parameter(description = "标签ID", required = true) 
            @PathVariable @NotNull @Positive Long id) {
        log.info("[标签删除] 开始处理删除标签请求 - 标签ID: {}", id);
        
        return tagService.deleteTag(id)
                .doOnSuccess(success -> log.info("[标签删除] 标签删除成功 - 标签ID: {}", id))
                .thenReturn(Result.success(MessageConstants.TAG_DELETE_SUCCESS))
                .doOnError(e -> log.error("[标签删除] 标签删除失败 - 标签ID: {}, 错误: {}", 
                        id, e.getMessage(), e));
    }

    /**
     * 获取所有标签
     * <p>
     * 查询系统中所有未删除的标签。可选择是否包含每个标签关联的文章数量。
     * 结果会被缓存以提高查询性能。
     * 
     * @param withCount 是否包含文章数量统计，默认为false
     * @return 标签列表
     */
    @Operation(summary = "获取所有标签", description = "获取所有标签列表，可选择是否包含文章数量")
    @GetMapping("/list")
    public Mono<Result<List<TagVO>>> getAllTags(
            @Parameter(description = "是否包含文章数量统计") 
            @RequestParam(defaultValue = "false") boolean withCount) {
        log.info("[标签列表] 查询所有标签 - 是否包含文章数: {}", withCount);
        
        return tagService.getAllTags(withCount)
                .collectList()
                .doOnSuccess(tags -> log.info("[标签列表] 标签列表查询成功 - 标签总数: {}", tags.size()))
                .map(Result::success);
    }

    /**
     * 获取文章标签
     * <p>
     * 查询指定文章关联的所有标签。结果会被缓存以提高查询性能。
     * 
     * @param articleId 文章ID，必须为正整数
     * @return 标签列表
     */
    @Operation(summary = "获取文章标签", description = "根据文章ID获取该文章关联的所有标签")
    @GetMapping("/article/{articleId}")
    public Mono<Result<List<TagVO>>> getTagsByArticleId(
            @Parameter(description = "文章ID", required = true) 
            @PathVariable @NotNull @Positive Long articleId) {
        log.info("[文章标签] 查询文章标签 - 文章ID: {}", articleId);
        
        return tagService.getTagsByArticleId(articleId)
                .collectList()
                .doOnSuccess(tags -> log.info("[文章标签] 文章标签查询成功 - 文章ID: {}, 标签数: {}", 
                        articleId, tags.size()))
                .map(Result::success);
    }

    /**
     * 为文章添加标签
     * <p>
     * 为指定文章添加一个或多个标签。会先删除文章原有的所有标签关联，再建立新的关联关系。
     * 操作成功后会清除相关缓存。
     * 
     * @param articleId 文章ID，必须为正整数
     * @param tagIds 标签ID列表，不能为空
     * @return 操作结果，包含成功消息
     */
    @Operation(summary = "为文章添加标签", description = "为文章添加标签，会覆盖原有标签")
    @PostMapping("/article/{articleId}")
    public Mono<Result<String>> addTagsToArticle(
            @Parameter(description = "文章ID", required = true) 
            @PathVariable @NotNull @Positive Long articleId,
            @Parameter(description = "标签ID列表", required = true) 
            @RequestBody @NotEmpty List<@NotNull @Positive Long> tagIds) {
        log.info("[文章标签] 为文章添加标签 - 文章ID: {}, 标签IDs: {}", articleId, tagIds);
        
        return tagService.addTagsToArticle(articleId, tagIds)
                .doOnSuccess(success -> log.info("[文章标签] 文章标签添加成功 - 文章ID: {}, 标签数: {}", 
                        articleId, tagIds.size()))
                .map(success -> Result.success(MessageConstants.TAG_ASSIGN_SUCCESS))
                .doOnError(e -> log.error("[文章标签] 文章标签添加失败 - 文章ID: {}, 错误: {}", 
                        articleId, e.getMessage(), e));
    }

    /**
     * 移除文章标签
     * <p>
     * 移除指定文章的所有标签关联关系。操作成功后会清除相关缓存。
     * 
     * @param articleId 文章ID，必须为正整数
     * @return 操作结果，包含成功消息
     */
    @Operation(summary = "移除文章标签", description = "移除文章的所有标签关联")
    @DeleteMapping("/article/{articleId}")
    public Mono<Result<String>> removeTagsFromArticle(
            @Parameter(description = "文章ID", required = true) 
            @PathVariable @NotNull @Positive Long articleId) {
        log.info("[文章标签] 移除文章标签 - 文章ID: {}", articleId);
        
        return tagService.removeTagsFromArticle(articleId)
                .doOnSuccess(success -> log.info("[文章标签] 文章标签移除成功 - 文章ID: {}", articleId))
                .map(success -> Result.success(MessageConstants.TAG_REMOVE_SUCCESS))
                .doOnError(e -> log.error("[文章标签] 文章标签移除失败 - 文章ID: {}, 错误: {}", 
                        articleId, e.getMessage(), e));
    }

    /**
     * 检查标签名称
     * <p>
     * 检查指定的标签名称是否已被使用。用于创建或更新标签前的名称唯一性校验。
     * 
     * @param name 标签名称，不能为空
     * @return true表示名称已存在，false表示名称可用
     */
    @Operation(summary = "检查标签名称", description = "检查标签名称是否已存在，用于唯一性校验")
    @GetMapping("/check")
    public Mono<Result<Boolean>> checkTagName(
            @Parameter(description = "标签名称", required = true) 
            @RequestParam @NotEmpty String name) {
        log.info("[标签校验] 检查标签名称是否存在 - 标签名称: {}", name);
        
        return tagService.checkTagNameExists(name)
                .doOnSuccess(exists -> log.debug("[标签校验] 标签名称检查完成 - 标签名称: {}, 是否存在: {}", 
                        name, exists))
                .map(Result::success);
    }

    /**
     * 获取热门标签
     * <p>
     * 根据标签关联的文章数量，查询最热门的标签列表。结果会被缓存以提高查询性能。
     * 
     * @param limit 返回的标签数量限制，默认为10，必须为正整数
     * @return 热门标签列表，按文章数量降序排列
     */
    @Operation(summary = "获取热门标签", description = "获取热门标签列表，按文章数量降序排列")
    @GetMapping("/hot")
    public Mono<Result<List<TagVO>>> getHotTags(
            @Parameter(description = "返回数量限制") 
            @RequestParam(defaultValue = "10") @Positive int limit) {
        log.info("[热门标签] 查询热门标签 - 限制数量: {}", limit);
        
        return tagService.getHotTags(limit)
                .collectList()
                .doOnSuccess(tags -> log.info("[热门标签] 热门标签查询成功 - 返回数量: {}", tags.size()))
                .map(Result::success);
    }


    /**
     * 分页查询标签列表
     * <p>
     * 支持关键字搜索的标签分页查询。可根据标签名称或描述进行模糊搜索。
     * 结果会被缓存以提高查询性能。
     * 
     * @param tagListDTO 分页查询参数，包含页码、每页大小和搜索关键字
     * @return 分页结果，包含标签列表和分页信息
     */
    @Operation(
            summary = "分页查询标签列表",
            description = "支持关键字搜索的标签分页查询，可根据标签名称或描述进行模糊搜索")
    @GetMapping("/page")
    public Mono<Result<PageResult<TagVO>>> getTagByPage(
            @ParameterObject @Valid TagListDTO tagListDTO) {
        log.info("[标签分页] 分页查询标签 - 页码: {}, 每页大小: {}, 关键字: {}", 
                tagListDTO.getCurrentPage(), tagListDTO.getPageSize(), tagListDTO.getKeyword());
        
        return tagService.getTagByPage(tagListDTO)
                .doOnSuccess(result -> log.info("[标签分页] 分页查询成功 - 总记录数: {}, 总页数: {}, 当前页记录数: {}", 
                        result.getTotal(), result.getPages(), result.getRecords().size()))
                .map(Result::success)
                .doOnError(e -> log.error("[标签分页] 分页查询失败 - 错误: {}", e.getMessage(), e));
    }
} 