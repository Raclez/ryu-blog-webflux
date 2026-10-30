package com.ryu.blog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ryu.blog.constant.PermissionConstants;
import com.ryu.blog.dto.StorageConfigCreateDTO;
import com.ryu.blog.dto.StorageConfigQueryDTO;
import com.ryu.blog.dto.StorageConfigUpdateDTO;
import com.ryu.blog.service.StorageConfigService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.StorageConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
// 存储配置里含对象存储 AK/SK —— 读写都需要管理员权限
@SaCheckPermission(PermissionConstants.SYSTEM_STORAGE_VIEW)
public class StorageConfigController {

    private final StorageConfigService storageConfigService;

    /**
     * 获取存储策略信息
     *
     * <p>返回的是<b>脱敏后的</b>视图 —— accessKey / secretKey 之类的
     * 凭据不会出现在响应里。
     *
     * @param strategyKey 存储策略的唯一标识
     * @return 存储策略信息
     */
    @Operation(summary = "根据key获取存储策略信息")
    @GetMapping("/{strategyKey}")
    public Mono<Result<StorageConfigVO>> getStorageConfig(@PathVariable String strategyKey) {
        log.info("获取存储策略信息: strategyKey={}", strategyKey);

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
    public Mono<Result<PageResult<StorageConfigVO>>> getStorageConfigByPage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String strategyName) {

        log.info("分页获取存储策略: page={}, size={}, name={}", currentPage, pageSize, strategyName);

        StorageConfigQueryDTO queryDTO = new StorageConfigQueryDTO();
        queryDTO.setCurrentPage(currentPage);
        queryDTO.setPageSize(pageSize);
        queryDTO.setStrategyName(strategyName);

        return storageConfigService.getStrategiesByPage(queryDTO)
                .collectList()
                .map(list -> {
                    PageResult<StorageConfigVO> pageResult = new PageResult<>();
                    pageResult.setRecords(list);
                    pageResult.setCurrent(currentPage);
                    pageResult.setSize(pageSize);

                    // 注意：实际项目中应该单独查询总数
                    // 这里简化处理，直接使用列表大小作为总数
                    pageResult.setTotal(list.size());

                    log.debug("分页获取存储策略成功: 数量={}", list.size());
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
    @SaCheckPermission(PermissionConstants.SYSTEM_STORAGE_EDIT)
    @PostMapping("/add")
    public Mono<Result<Boolean>> createStorageConfig(@Valid @RequestBody StorageConfigCreateDTO storageConfigCreateDTO) {
        log.info("创建存储策略: strategyKey={}", storageConfigCreateDTO.getStrategyKey());
        
        return storageConfigService.createStorageConfig(storageConfigCreateDTO)
                .then(Mono.just(Result.success(true)))
                .doOnSuccess(result -> log.info("创建存储策略成功: strategyKey={}", 
                        storageConfigCreateDTO.getStrategyKey()));
    }

    /**
     * 更新存储策略
     *
     * @param storageConfigUpdateDTO 存储策略配置
     * @return 操作结果
     */
    @Operation(summary = "更新存储策略")
    @SaCheckPermission(PermissionConstants.SYSTEM_STORAGE_EDIT)
    @PutMapping("/edit")
    public Mono<Result<Boolean>> editStorageConfig(@Valid @RequestBody StorageConfigUpdateDTO storageConfigUpdateDTO) {
        log.info("更新存储策略: id={}", storageConfigUpdateDTO.getId());
        
        return storageConfigService.updateStorageConfig(storageConfigUpdateDTO)
                .then(Mono.just(Result.success(true)))
                .doOnSuccess(result -> log.info("更新存储策略成功: id={}", storageConfigUpdateDTO.getId()));
    }

    /**
     * 删除存储策略
     *
     * @param id 存储策略的唯一标识
     * @return 删除成功的响应
     */
    @Operation(summary = "删除存储策略")
    @SaCheckPermission(PermissionConstants.SYSTEM_STORAGE_EDIT)
    @DeleteMapping("/delete/{id}")
    public Mono<Result<Boolean>> deleteStorageConfig(@PathVariable Long id) {
        log.info("删除存储策略: id={}", id);
        
        return storageConfigService.removeById(id)
                .then(Mono.just(Result.success(true)))
                .doOnSuccess(result -> log.info("删除存储策略成功: id={}", id));
    }

    /**
     * 查询激活存储策略
     *
     * @return 激活的储策略信息
     */
    @Operation(summary = "查询激活存储策略")
    @GetMapping("/enabled")
    public Mono<Result<StorageConfigVO>> getEnabledStorageConfig() {
        log.info("查询激活存储策略");
        
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
    @SaCheckPermission(PermissionConstants.SYSTEM_STORAGE_EDIT)
    @PutMapping("/enable/{strategyKey}")
    public Mono<Result<Boolean>> enableStorageConfig(@PathVariable String strategyKey) {
        log.info("启用存储策略: strategyKey={}", strategyKey);
        
        return storageConfigService.enableOrDisableStorageConfig(strategyKey, true)
                .then(Mono.just(Result.success(true)))
                .doOnSuccess(result -> log.info("启用存储策略成功: strategyKey={}", strategyKey));
    }
    
    /**
     * 禁用存储策略
     *
     * @param strategyKey 存储策略的唯一标识
     * @return 操作结果
     */
    @Operation(summary = "禁用存储策略")
    @SaCheckPermission(PermissionConstants.SYSTEM_STORAGE_EDIT)
    @PutMapping("/disable/{strategyKey}")
    public Mono<Result<Boolean>> disableStorageConfig(@PathVariable String strategyKey) {
        log.info("禁用存储策略: strategyKey={}", strategyKey);
        
        return storageConfigService.enableOrDisableStorageConfig(strategyKey, false)
                .then(Mono.just(Result.success(true)))
                .doOnSuccess(result -> log.info("禁用存储策略成功: strategyKey={}", strategyKey));
    }
}
