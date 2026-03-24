package com.ryu.blog.controller;

import com.ryu.blog.dto.SysDictTypeAddDTO;
import com.ryu.blog.dto.SysDictTypeUpdateDTO;
import com.ryu.blog.dto.SysDictTypeQueryDTO;
import com.ryu.blog.service.SysDictTypeService;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictTypeVO;
import com.ryu.blog.utils.Result;
import com.ryu.blog.constant.MessageConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 系统字典类型控制器
 *
 * @author ryu 475118582@qq.com
 */
@Validated
@RestController
@RequestMapping("/sysDictType")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "系统字典类型", description = "系统字典类型管理接口")
public class SysDictTypeController {

    private final SysDictTypeService dictTypeService;

    /**
     * 查询相关接口
     */
    
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取字典类型", description = "通过字典类型ID获取详细信息")
    public Mono<Result<SysDictTypeVO>> getDictTypeById(
            @Parameter(description = "字典类型ID") @PathVariable Long id) {
        log.info("根据ID获取字典类型，id={}", id);
        return dictTypeService.getDictTypeById(id)
                .map(Result::success);
    }
    
    @GetMapping("/code/{dictType}")
    @Operation(summary = "根据编码获取字典类型", description = "通过字典类型编码获取详细信息")
    public Mono<Result<SysDictTypeVO>> getDictTypeByCode(
            @Parameter(description = "字典类型编码") @PathVariable String dictType) {
        log.info("根据编码获取字典类型，dictType={}", dictType);
        return dictTypeService.getDictTypeByCode(dictType)
                .map(Result::success)
                .defaultIfEmpty(Result.fail("字典类型不存在"));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询字典类型", description = "支持按名称和编码查询")
    public Mono<Result<PageResult<SysDictTypeVO>>> getSysDictTypePage(
            @ParameterObject SysDictTypeQueryDTO queryDTO
    ) {
        log.info("分页查询字典类型，查询条件: {}", queryDTO);
        return dictTypeService.getDictTypePage(queryDTO)
                .map(Result::success);
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有字典类型", description = "获取所有启用的字典类型列表")
    public Mono<Result<List<SysDictTypeVO>>> getAllSysDictTypes() {
        log.info("查询所有字典类型");
        return dictTypeService.getAllDictTypes()
                .collectList()
                .map(Result::success);
    }

    /**
     * 数据操作接口
     */
    
    @PostMapping("/add")
    @Operation(summary = "创建字典类型", description = "添加新的字典类型")
    public Mono<Result<Void>> createDictType(@Valid @RequestBody SysDictTypeAddDTO dictTypeDTO) {
        log.info("创建字典类型: {}", dictTypeDTO);
        return dictTypeService.createDictType(dictTypeDTO)
                .then(Mono.just(Result.successMsg(MessageConstants.DICT_TYPE_CREATE_SUCCESS)));
    }

    @PutMapping("/edit")
    @Operation(summary = "更新字典类型", description = "修改字典类型信息")
    public Mono<Result<Void>> updateDictType(@Valid @RequestBody SysDictTypeUpdateDTO dictTypeDTO) {
        log.info("更新字典类型: {}", dictTypeDTO);
        return dictTypeService.updateDictType(dictTypeDTO)
                .then(Mono.just(Result.successMsg(MessageConstants.DICT_TYPE_UPDATE_SUCCESS)));
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除字典类型", description = "删除字典类型及其关联的字典项")
    public Mono<Result<Void>> deleteDictType(
            @Parameter(description = "字典类型ID") @PathVariable Long id) {
        log.info("删除字典类型，ID: {}", id);
        return dictTypeService.deleteDictType(id)
                .then(Mono.just(Result.successMsg(MessageConstants.DICT_TYPE_DELETE_SUCCESS)));
    }
    
    @PostMapping("/delete/batch")
    @Operation(summary = "批量删除字典类型", description = "批量删除字典类型及其关联的字典项")
    public Mono<Result<Void>> batchDeleteSysDictType(
            @Parameter(description = "字典类型ID列表") @RequestBody List<String> ids) {
        log.info("批量删除字典类型，IDs: {}", ids);
        return dictTypeService.batchDeleteDictTypes(ids)
                .then(Mono.just(Result.successMsg(MessageConstants.DICT_TYPE_BATCH_DELETE_SUCCESS)));
    }
    
    /**
     * 缓存管理接口
     */
    
    @DeleteMapping("/cache/clear")
    @Operation(summary = "清除所有字典类型缓存", description = "清除字典类型相关的所有缓存")
    public Mono<Result<Boolean>> clearCache() {
        log.info("清除所有字典类型缓存");
        return dictTypeService.clearAllCache()
                .map(Result::success);
    }
    
    @DeleteMapping("/cache/refresh/{dictType}")
    @Operation(summary = "刷新指定字典类型缓存", description = "刷新指定字典类型的缓存")
    public Mono<Result<Boolean>> refreshCache(
            @Parameter(description = "字典类型编码") @PathVariable String dictType) {
        log.info("刷新字典类型缓存: {}", dictType);
        return dictTypeService.refreshCache(dictType)
                .map(Result::success);
    }
} 