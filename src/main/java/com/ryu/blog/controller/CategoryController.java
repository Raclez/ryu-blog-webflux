package com.ryu.blog.controller;

import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.dto.CategoryCreateDTO;
import com.ryu.blog.dto.CategoryListDTO;
import com.ryu.blog.dto.CategoryUpdateDTO;
import com.ryu.blog.service.CategoryService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.CategoryStatsVO;
import com.ryu.blog.vo.CategoryVO;
import com.ryu.blog.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/category")
@RequiredArgsConstructor
@Validated
@Tag(name = "分类管理", description = "分类相关接口")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "创建分类", description = "创建新分类")
    @PostMapping("/save")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Result<String>> createCategory(@RequestBody @Validated CategoryCreateDTO categoryCreateDTO) {
        log.info("创建分类请求: {}", categoryCreateDTO);
        return categoryService.createCategory(categoryCreateDTO)
                .doOnSuccess(v -> log.info("分类创建成功: {}", categoryCreateDTO.getName()))
                .thenReturn(Result.success(MessageConstants.CATEGORY_CREATE_SUCCESS))
                .doOnError(e -> log.error("创建分类失败: {}", e.getMessage(), e));
    }

    @Operation(summary = "更新分类", description = "更新已有分类")
    @PutMapping("/edit")
    public Mono<Result<String>> updateCategory(@RequestBody @Validated CategoryUpdateDTO categoryUpdateDTO) {
        log.info("更新分类请求: {}", categoryUpdateDTO);
        return categoryService.updateCategory(categoryUpdateDTO)
                .doOnSuccess(v -> log.info("分类更新成功: id={}", categoryUpdateDTO.getId()))
                .thenReturn(Result.success(MessageConstants.CATEGORY_UPDATE_SUCCESS))
                .doOnError(e -> log.error("更新分类失败: {}", e.getMessage(), e));
    }

    @Operation(summary = "获取分类详情", description = "根据ID获取分类详情")
    @GetMapping("/detail/{id}")
    public Mono<Result<CategoryVO>> getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id)
                .map(Result::success);
    }

    @Operation(summary = "删除分类", description = "根据ID删除分类")
    @DeleteMapping("/delete/{id}")
    public Mono<Result<String>> deleteCategory(@PathVariable Long id) {
        log.info("删除分类请求: id={}", id);
        return categoryService.deleteCategory(id)
                .doOnSuccess(v -> log.info("分类删除成功: id={}", id))
                .thenReturn(Result.success(MessageConstants.CATEGORY_DELETE_SUCCESS))
                .doOnError(e -> log.error("删除分类失败: {}", e.getMessage(), e));
    }

    @Operation(summary = "获取所有分类基本信息", description = "获取所有分类的基本信息列表")
    @GetMapping("/all")
    public Mono<Result<List<CategoryVO>>> getAllCategories() {
        return categoryService.getAllCategories()
                .collectList()
                .map(Result::success);
    }

    @Operation(summary = "获取所有分类统计信息", description = "获取所有分类的统计信息列表，包含文章数量")
    @GetMapping("/stats")
    public Mono<Result<List<CategoryStatsVO>>> getAllCategoriesWithStats() {
        return categoryService.getAllCategoriesWithArticleCount()
                .collectList()
                .map(Result::success);
    }

    @Operation(summary = "分页查询分类", description = "根据条件分页查询分类")
    @GetMapping("/page")
    public Mono<Result<PageResult<CategoryVO>>> getCategoriesByPage(@ParameterObject @Valid CategoryListDTO categoryListDTO) {
        log.info("分页查询分类请求: {}", categoryListDTO);
        return categoryService.getCategoriesByPage(categoryListDTO)
                .map(Result::success)
                .doOnSuccess(result -> log.info("分页查询分类成功，总数: {}", result.getData().getTotal()))
                .doOnError(e -> log.error("分页查询分类失败: {}", e.getMessage(), e));
    }

    @Operation(summary = "检查分类名称", description = "检查分类名称是否已存在")
    @GetMapping("/check")
    public Mono<Result<Boolean>> checkCategoryName(
            @Parameter(description = "分类名称") @RequestParam String name) {
        return categoryService.checkCategoryNameExists(name)
                .map(Result::success);
    }
} 