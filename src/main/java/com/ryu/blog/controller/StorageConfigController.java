package com.ryu.blog.controller;

import com.ryu.blog.dto.StorageConfigCreateDTO;
import com.ryu.blog.dto.StorageConfigQueryDTO;
import com.ryu.blog.dto.StorageConfigUpdateDTO;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.service.StorageConfigService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 存储策略控制器
 *
 * @author ryu 475118582@qq.com
 */
@RestController
@RequestMapping("/storageConfigs")
@Tag(name = "存储策略管理", description = "用于管理不同存储服务的配置")
@RequiredArgsConstructor
@Slf4j
public class StorageConfigController {

    private final StorageConfigService storageConfigService;

    /**
     * 获取存储策略信息
     *
     * @param strategyKey 存储策略的唯一标识
     * @return 存储策略信息
     */
    @Operation(summary = "根据key获取存储策略信息")
    @GetMapping("/{strategyKey}")
    public Mono<Result<StorageConfig>> getStorageConfig(@PathVariable String strategyKey) {
        return storageConfigService.getStrategyByKey(strategyKey)
                .map(Result::success)
                .defaultIfEmpty(Result.notFound("存储策略不存在"));
    }

    /**
     * 分页获取存储策略信息
     *
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     * @param strategyName 策略名称（可选）
     * @return 存储策略信息
     */
    @Operation(summary = "分页获取存储策略信息")
    @GetMapping("/page")
    public Mono<Result<PageResult<StorageConfig>>> getStorageConfigByPage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String strategyName) {
        
        StorageConfigQueryDTO queryDTO = new StorageConfigQueryDTO();
        queryDTO.setCurrentPage(currentPage);
        queryDTO.setPageSize(pageSize);
        queryDTO.setStrategyName(strategyName);
        
        return storageConfigService.getStrategiesByPage(queryDTO)
                .collectList()
                .map(list -> {
                    PageResult<StorageConfig> pageResult = new PageResult<>();
                    pageResult.setRecords(list);
                    pageResult.setCurrent(currentPage);
                    pageResult.setSize(pageSize);
                    
                    // 注意：实际项目中应该单独查询总数
                    // 这里简化处理，直接使用列表大小作为总数
                    pageResult.setTotal(list.size());
                    
                    return Result.success(pageResult);
                });
    }

    /**
     * 创建存储策略
     *
     * @param storageConfigCreateDTO 存储策略配置
     * @return 操作结果
     */
    @Operation(summary = "创建存储策略")
    @PostMapping("/add")
    public Mono<Result<Boolean>> createStorageConfig(@RequestBody StorageConfigCreateDTO storageConfigCreateDTO) {
        return storageConfigService.createStorageConfig(storageConfigCreateDTO)
                .then(Mono.just(Result.success(true)))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 更新存储策略
     *
     * @param storageConfigUpdateDTO 存储策略配置
     * @return 操作结果
     */
    @Operation(summary = "更新存储策略")
    @PutMapping("/edit")
    public Mono<Result<Boolean>> editStorageConfig(@RequestBody StorageConfigUpdateDTO storageConfigUpdateDTO) {
        return storageConfigService.updateStorageConfig(storageConfigUpdateDTO)
                .then(Mono.just(Result.success(true)))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 删除存储策略
     *
     * @param id 存储策略的唯一标识
     * @return 删除成功的响应
     */
    @Operation(summary = "删除存储策略")
    @DeleteMapping("/delete/{id}")
    public Mono<Result<Boolean>> deleteStorageConfig(@PathVariable Long id) {
        return storageConfigService.removeById(id)
                .then(Mono.just(Result.success(true)))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    /**
     * 查询激活存储策略
     *
     * @return 激活的储策略信息
     */
    @Operation(summary = "查询激活存储策略")
    @GetMapping("/enabled")
    public Mono<Result<StorageConfig>> getEnabledStorageConfig() {
        return storageConfigService.getEnabledStrategy()
                .map(Result::success)
                .defaultIfEmpty(Result.successMsg("没有激活的存储策略"));
    }
    
    /**
     * 启用存储策略
     *
     * @param strategyKey 存储策略的唯一标识
     * @return 操作结果
     */
    @Operation(summary = "启用存储策略")
    @PutMapping("/enable/{strategyKey}")
    public Mono<Result<Boolean>> enableStorageConfig(@PathVariable String strategyKey) {
        return storageConfigService.enableOrDisableStorageConfig(strategyKey, true)
                .then(Mono.just(Result.success(true)))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
    
    /**
     * 禁用存储策略
     *
     * @param strategyKey 存储策略的唯一标识
     * @return 操作结果
     */
    @Operation(summary = "禁用存储策略")
    @PutMapping("/disable/{strategyKey}")
    public Mono<Result<Boolean>> disableStorageConfig(@PathVariable String strategyKey) {
        return storageConfigService.enableOrDisableStorageConfig(strategyKey, false)
                .then(Mono.just(Result.success(true)))
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
} 