package com.ryu.blog.controller;

import com.ryu.blog.dto.SysDictItemDTO;
import com.ryu.blog.dto.SysDictItemSaveDTO;
import com.ryu.blog.dto.SysDictItemUpdateDTO;
import com.ryu.blog.service.SysDictItemService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictItemVO;
import com.ryu.blog.constant.MessageConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 系统字典项控制器
 *
 * @author ryu 475118582@qq.com
 */
@Validated
@RestController
@RequestMapping("/sysDictItem")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "系统字典项", description = "系统字典项管理接口")
public class SysDictItemController {

    private final SysDictItemService dictItemService;

    /**
     * 查询相关接口
     */

    @GetMapping("/page")
    @Operation(summary = "分页查询字典项", description = "支持按字典类型和标签查询")
    public Mono<Result<PageResult<SysDictItemVO>>> getSysDictItem(
            @ParameterObject SysDictItemDTO sysDictItemDTO) {
        log.info("分页查询字典项，查询条件: {}", sysDictItemDTO);
        return dictItemService.getDictItemPage(sysDictItemDTO)
                .map(Result::success);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取字典项", description = "通过字典项ID获取详细信息")
    public Mono<Result<SysDictItemVO>> getDictItemById(
            @Parameter(description = "字典项ID") @PathVariable Long id) {
        log.info("根据ID获取字典项，id={}", id);
        return dictItemService.getDictItemById(id)
                .map(Result::success);
    }
    
    @GetMapping("/type/{dictType}")
    @Operation(summary = "根据字典类型编码获取字典项", description = "获取指定字典类型的所有启用字典项")
    public Mono<Result<List<SysDictItemVO>>> getDictItemsByType(
            @Parameter(description = "字典类型编码", example = "gender_complete") @PathVariable String dictType) {
        log.info("根据字典类型编码获取字典项，dictType={}", dictType);
        return dictItemService.getDictItemsByType(dictType)
                .collectList()
                .map(Result::success);
    }
    
    @PostMapping("/batch")
    @Operation(summary = "批量获取字典项", description = "一次性获取多个字典类型的字典项")
    public Mono<Result<Map<String, List<SysDictItemVO>>>> batchGetDictItems(
            @Parameter(description = "字典类型编码列表") 
            @RequestBody @NotEmpty(message = "字典类型列表不能为空") List<String> dictTypes) {
        log.info("批量获取字典项，dictTypes={}", dictTypes);
        return dictItemService.batchGetDictItems(dictTypes)
                .map(Result::success);
    }
    
    @GetMapping("/value")
    @Operation(summary = "获取字典项值", description = "根据字典类型和键获取字典项值")
    public Mono<Result<String>> getDictItemValue(
            @Parameter(description = "字典类型编码") @RequestParam String dictType,
            @Parameter(description = "字典项键") @RequestParam String key,
            @Parameter(description = "默认值") @RequestParam(required = false) String defaultValue) {
        log.info("获取字典项值，dictType={}, key={}, defaultValue={}", dictType, key, defaultValue);
        return dictItemService.getDictItemValue(dictType, key, defaultValue)
                .map(Result::success);
    }

    /**
     * 数据操作接口
     */

    @PostMapping("/save")
    @Operation(summary = "创建字典项", description = "添加新的字典项")
    public Mono<Result<Void>> createDictItem(@Valid @RequestBody SysDictItemSaveDTO saveDTO) {
        log.info("创建字典项: {}", saveDTO);
        return dictItemService.createDictItem(saveDTO)
                .then(Mono.just(Result.<Void>successMsg(MessageConstants.DICT_ITEM_CREATE_SUCCESS)));
    }

    @PutMapping("/edit")
    @Operation(summary = "更新字典项", description = "修改字典项信息")
    public Mono<Result<Void>> updateDictItem(@Valid @RequestBody SysDictItemUpdateDTO dictItemDTO) {
        log.info("更新字典项: {}", dictItemDTO);
        return dictItemService.updateDictItem(dictItemDTO)
                .then(Mono.just(Result.<Void>successMsg(MessageConstants.DICT_ITEM_UPDATE_SUCCESS)));
    }

    @PutMapping("/updateStatus")
    @Operation(summary = "修改字典项状态", description = "启用或禁用字典项")
    public Mono<Result<Void>> editStatus(
            @Parameter(description = "字典项ID") @RequestParam Long id, 
            @Parameter(description = "状态：true-启用，false-禁用") @RequestParam Boolean status) {
        log.info("修改字典项状态，ID: {}, 状态: {}", id, status);
        return dictItemService.updateDictItemStatus(id, status)
                .then(Mono.just(Result.<Void>successMsg(MessageConstants.DICT_ITEM_STATUS_UPDATE_SUCCESS)));
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除字典项", description = "删除指定的字典项")
    public Mono<Result<Void>> deleteDictItem(
            @Parameter(description = "字典项ID") @PathVariable Long id) {
        log.info("删除字典项，ID: {}", id);
        return dictItemService.deleteDictItem(id)
                .then(Mono.just(Result.<Void>successMsg(MessageConstants.DICT_ITEM_DELETE_SUCCESS)));
    }
    
    /**
     * 缓存管理接口
     */
    
    @DeleteMapping("/cache/clear")
    @Operation(summary = "清除所有字典项缓存", description = "清除字典项相关的所有缓存")
    public Mono<Result<Boolean>> clearCache() {
        log.info("清除所有字典项缓存");
        return dictItemService.clearAllCache()
                .map(Result::success);
    }
    
    @DeleteMapping("/cache/refresh/{dictType}")
    @Operation(summary = "刷新指定字典类型的字典项缓存", description = "刷新指定字典类型的字典项缓存")
    public Mono<Result<Boolean>> refreshCache(
            @Parameter(description = "字典类型编码") @PathVariable String dictType) {
        log.info("刷新字典项缓存: {}", dictType);
        return dictItemService.refreshCache(dictType)
                .map(Result::success);
    }
} 