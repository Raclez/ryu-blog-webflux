package com.ryu.blog.controller;

import com.ryu.blog.dto.SysDictItemDTO;
import com.ryu.blog.dto.SysDictItemSaveDTO;
import com.ryu.blog.dto.SysDictItemUpdateDTO;
import com.ryu.blog.service.SysDictItemService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictItemVO;
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
 * 系统字典项控制器
 *
 * @author ryu 475118582@qq.com
 */
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

    @GetMapping("/getDictItems")
    @Operation(summary = "分页查询字典项")
    public Mono<Result<PageResult<SysDictItemVO>>> getSysDictItem(
            @ParameterObject SysDictItemDTO sysDictItemDTO) {
        log.info("开始分页查询字典项, 查询条件: {}", sysDictItemDTO);
        return dictItemService.getDictItemPage(sysDictItemDTO)
                .map(Result::success);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取字典项")
    public Mono<Result<SysDictItemVO>> getDictItemById(@PathVariable Long id) {
        return dictItemService.getDictItemById(id)
                .map(Result::success);
    }
    
    @GetMapping("/condition")
    @Operation(summary = "根据字典类型编码获取字典项")
    public Mono<Result<List<SysDictItemVO>>> getDictItemsByType(@RequestParam("key") String dictType) {
        SysDictItemDTO queryDTO = new SysDictItemDTO();
        queryDTO.setDictType(dictType);
        // 不分页，获取所有启用的字典项
        queryDTO.setPageSize(1000L); // 设置一个足够大的值
        
        return dictItemService.getDictItemPage(queryDTO)
                .map(pageResult -> Result.success(pageResult.getRecords()));
    }

    /**
     * 数据操作接口
     */

    @PostMapping("/save")
    @Operation(summary = "创建字典项")
    public Mono<Result<Void>> createDictItem(@Valid @RequestBody SysDictItemSaveDTO saveDTO) {
        log.info("开始创建字典项: {}", saveDTO);
        return dictItemService.createDictItem(saveDTO)
                .then(Mono.just(Result.<Void>successMsg(MessageConstants.DICT_ITEM_CREATE_SUCCESS)));
    }

    @PutMapping("/edit")
    @Operation(summary = "更新字典项")
    public Mono<Result<Void>> updateDictItem(@Valid @RequestBody SysDictItemUpdateDTO dictItemDTO) {
        log.info("开始更新字典项: {}", dictItemDTO);
        return dictItemService.updateDictItem(dictItemDTO)
                .then(Mono.just(Result.<Void>successMsg(MessageConstants.DICT_ITEM_UPDATE_SUCCESS)));
    }

    @PutMapping("/updateStatus")
    @Operation(summary = "修改字典项状态")
    public Mono<Result<Void>> editStatus(@RequestParam Long id, @RequestParam Boolean status) {
        log.info("开始修改字典项状态, ID: {}, 状态: {}", id, status);
        return dictItemService.updateDictItemStatus(id, status)
                .then(Mono.just(Result.<Void>successMsg(MessageConstants.DICT_ITEM_STATUS_UPDATE_SUCCESS)));
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除字典项")
    public Mono<Result<Void>> deleteDictItem(@PathVariable Long id) {
        log.info("开始删除字典项, ID: {}", id);
        return dictItemService.deleteDictItem(id)
                .then(Mono.just(Result.<Void>successMsg(MessageConstants.DICT_ITEM_DELETE_SUCCESS)));
    }
} 