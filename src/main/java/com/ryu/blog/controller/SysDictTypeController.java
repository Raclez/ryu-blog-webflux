package com.ryu.blog.controller;

import com.ryu.blog.dto.SysDictTypeAddDTO;
import com.ryu.blog.dto.SysDictTypeUpdateDTO;
import com.ryu.blog.dto.sysDictTypeQueryDTO;
import com.ryu.blog.service.SysDictTypeService;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictTypeVO;
import com.ryu.blog.utils.Result;
import com.ryu.blog.constant.MessageConstants;
import com.ryu.blog.constant.SystemConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 系统字典类型控制器
 *
 * @author ryu 475118582@qq.com
 */
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
    @Operation(summary = "根据ID获取字典类型")
    public Mono<Result<SysDictTypeVO>> getDictTypeById(@PathVariable Long id) {
        return dictTypeService.getDictTypeById(id)
                .map(Result::success);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询字典类型")
    public Mono<Result<PageResult<SysDictTypeVO>>> getSysDictTypePage(
            @ParameterObject sysDictTypeQueryDTO queryDTO
    ) {
        log.info("开始分页查询字典类型, 查询条件: {}", queryDTO);
        return dictTypeService.getDictTypePage(queryDTO)
                .map(Result::success);
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有字典类型")
    public Mono<Result<List<SysDictTypeVO>>> getAllSysDictTypes() {
        log.info("开始查询所有字典项类型");
        return dictTypeService.getAllDictTypes()
                .collectList()
                .map(Result::success);
    }

    /**
     * 数据操作接口
     */
    
    @PostMapping("/add")
    @Operation(summary = "创建字典类型")
    public Mono<Result<Void>> createDictType(@Valid @RequestBody SysDictTypeAddDTO dictTypeDTO) {
        log.info("开始创建字典类型: {}", dictTypeDTO);
        return dictTypeService.createDictType(dictTypeDTO)
                .then(Mono.just(Result.successMsg(MessageConstants.DICT_TYPE_CREATE_SUCCESS)));
    }

    @PutMapping("/edit")
    @Operation(summary = "更新字典类型")
    public Mono<Result<Void>> updateDictType(@Valid @RequestBody SysDictTypeUpdateDTO dictTypeDTO) {
        log.info("开始更新字典类型: {}", dictTypeDTO);
        return dictTypeService.updateDictType(dictTypeDTO)
                .then(Mono.just(Result.successMsg(MessageConstants.DICT_TYPE_UPDATE_SUCCESS)));
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除字典类型")
    public Mono<Result<Void>> deleteDictType(@PathVariable Long id) {
        log.info("开始删除字典类型, ID: {}", id);
        return dictTypeService.deleteDictType(id)
                .then(Mono.just(Result.successMsg(MessageConstants.DICT_TYPE_DELETE_SUCCESS)));
    }
    
    @PostMapping("/delete/batch")
    @Operation(summary = "批量删除字典类型")
    public Mono<Result<Void>> batchDeleteSysDictType(@RequestBody List<String> ids) {
        log.info("开始批量删除字典类型, IDs: {}", ids);
        return dictTypeService.batchDeleteDictTypes(ids)
                .then(Mono.just(Result.successMsg(MessageConstants.DICT_TYPE_BATCH_DELETE_SUCCESS)));
    }
} 