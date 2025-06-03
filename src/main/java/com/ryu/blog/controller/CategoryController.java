package com.ryu.blog.controller;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.dto.CategoryCreateDTO;
import com.ryu.blog.dto.CategoryListDTO;
import com.ryu.blog.dto.CategoryUpdateDTO;
import com.ryu.blog.entity.Category;
import com.ryu.blog.service.CategoryService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.CategoryStatsVO;
import com.ryu.blog.vo.CategoryVO;
import com.ryu.blog.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 分类控制器
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "分类管理", description = "分类相关接口")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "创建分类", description = "创建新分类")
    @PostMapping("/save")
    public Mono<Result<String>> createCategory(@RequestBody @Validated CategoryCreateDTO categoryCreateDTO) {
        return categoryService.createCategory(categoryCreateDTO)
                .thenReturn(Result.success(MessageConstants.CATEGORY_CREATE_SUCCESS))
                .onErrorResume(e -> {
                    if (e.getMessage().contains(MessageConstants.CATEGORY_NAME_EXISTS)) {
                        return Mono.just(Result.error(ErrorCodeConstants.CATEGORY_NAME_EXISTS, e.getMessage()));
                    }
                    return Mono.just(Result.error(e.getMessage()));
                });
    }

    @Operation(summary = "更新分类", description = "更新已有分类")
    @PutMapping("/edit")
    public Mono<Result<String>> updateCategory(@RequestBody @Validated CategoryUpdateDTO categoryUpdateDTO) {
        return categoryService.updateCategory(categoryUpdateDTO)
                .then(Mono.just(Result.success(MessageConstants.CATEGORY_UPDATE_SUCCESS)))
                .onErrorResume(e -> Mono.just(Result.error(ErrorCodeConstants.CATEGORY_NOT_FOUND, e.getMessage())));
    }

    @Operation(summary = "获取分类详情", description = "根据ID获取分类详情")
    @GetMapping("/detail/{id}")
    public Mono<Result<CategoryVO>> getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(ErrorCodeConstants.CATEGORY_NOT_FOUND, e.getMessage())));
    }

    @Operation(summary = "删除分类", description = "根据ID删除分类")
    @DeleteMapping("/delete/{id}")
    public Mono<Result<String>> deleteCategory(@PathVariable Long id) {
        return categoryService.deleteCategory(id)
                .thenReturn(Result.success(MessageConstants.CATEGORY_DELETE_SUCCESS))
                .onErrorResume(e -> {
                    if (e.getMessage().contains(MessageConstants.CATEGORY_HAS_POSTS)) {
                        return Mono.just(Result.error(ErrorCodeConstants.CATEGORY_HAS_POSTS, e.getMessage()));
                    }
                    return Mono.just(Result.error(ErrorCodeConstants.CATEGORY_NOT_FOUND, e.getMessage()));
                });
    }

    @Operation(summary = "获取所有分类基本信息", description = "获取所有分类的基本信息列表")
    @GetMapping("/list")
    public Mono<Result<List<CategoryVO>>> getAllCategories() {
        return categoryService.getAllCategories()
                .collectList()
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "获取所有分类统计信息", description = "获取所有分类的统计信息列表，包含文章数量")
    @GetMapping("/stats")
    public Mono<Result<List<CategoryStatsVO>>> getAllCategoriesWithStats() {
        return categoryService.getAllCategoriesWithArticleCount()
                .collectList()
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "分页查询分类", description = "根据条件分页查询分类")
    @PostMapping("/page")
    public Mono<Result<PageResult<Category>>> getCategoriesByPage(@RequestBody CategoryListDTO categoryListDTO) {
        return categoryService.getCategoriesByPage(categoryListDTO)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "检查分类名称", description = "检查分类名称是否已存在")
    @GetMapping("/check")
    public Mono<Result<Boolean>> checkCategoryName(@RequestParam String name) {
        return categoryService.checkCategoryNameExists(name)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
} 