package com.ryu.blog.controller;

import com.ryu.blog.dto.StorageConfigCreateDTO;
import com.ryu.blog.dto.StorageConfigQueryDTO;
import com.ryu.blog.dto.StorageConfigUpdateDTO;
import com.ryu.blog.entity.StorageConfig;
import com.ryu.blog.service.StorageConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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
    public Mono<ResponseEntity<Map<String, Object>>> getStorageConfig(@PathVariable String strategyKey) {
        return storageConfigService.getStrategyByKey(strategyKey)
                .map(storageConfig -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "操作成功");
                    response.put("data", storageConfig);
                    return ResponseEntity.ok(response);
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("code", 404, "message", "存储策略不存在"))));
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
    public Mono<ResponseEntity<Map<String, Object>>> getStorageConfigByPage(
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
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "操作成功");
                    
                    Map<String, Object> data = new HashMap<>();
                    data.put("records", list);
                    data.put("total", list.size()); // 注意：实际项目中应该单独查询总数
                    data.put("size", pageSize);
                    data.put("current", currentPage);
                    
                    response.put("data", data);
                    return ResponseEntity.ok(response);
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
    public Mono<ResponseEntity<Map<String, Object>>> createStorageConfig(@RequestBody StorageConfigCreateDTO storageConfigCreateDTO) {
        return storageConfigService.createStorageConfig(storageConfigCreateDTO)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "code", 200,
                        "message", "存储策略创建成功"
                ))));
    }

    /**
     * 更新存储策略
     *
     * @param storageConfigUpdateDTO 存储策略配置
     * @return 操作结果
     */
    @Operation(summary = "更新存储策略")
    @PutMapping("/edit")
    public Mono<ResponseEntity<Map<String, Object>>> editStorageConfig(@RequestBody StorageConfigUpdateDTO storageConfigUpdateDTO) {
        return storageConfigService.updateStorageConfig(storageConfigUpdateDTO)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "code", 200,
                        "message", "存储策略更新成功"
                ))));
    }

    /**
     * 删除存储策略
     *
     * @param id 存储策略的唯一标识
     * @return 删除成功的响应
     */
    @Operation(summary = "删除存储策略")
    @DeleteMapping("/delete/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteStorageConfig(@PathVariable Long id) {
        return storageConfigService.removeById(id)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "code", 200,
                        "message", "存储策略删除成功"
                ))));
    }

    /**
     * 查询激活存储策略
     *
     * @return 激活的储策略信息
     */
    @Operation(summary = "查询激活存储策略")
    @GetMapping("/enabled")
    public Mono<ResponseEntity<Map<String, Object>>> getEnabledStorageConfig() {
        return storageConfigService.getEnabledStrategy()
                .map(storageConfig -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "操作成功");
                    response.put("data", storageConfig);
                    return ResponseEntity.ok(response);
                })
                .switchIfEmpty(createEmptyStrategyResponse());
    }
    
    /**
     * 创建空存储策略响应
     * @return 空存储策略响应
     */
    private Mono<ResponseEntity<Map<String, Object>>> createEmptyStrategyResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "没有激活的存储策略");
        response.put("data", null);
        return Mono.just(ResponseEntity.ok(response));
    }
    
    /**
     * 启用存储策略
     *
     * @param strategyKey 存储策略的唯一标识
     * @return 操作结果
     */
    @Operation(summary = "启用存储策略")
    @PutMapping("/enable/{strategyKey}")
    public Mono<ResponseEntity<Map<String, Object>>> enableStorageConfig(@PathVariable String strategyKey) {
        return storageConfigService.enableOrDisableStorageConfig(strategyKey, true)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "code", 200,
                        "message", "存储策略启用成功"
                ))));
    }
    
    /**
     * 禁用存储策略
     *
     * @param strategyKey 存储策略的唯一标识
     * @return 操作结果
     */
    @Operation(summary = "禁用存储策略")
    @PutMapping("/disable/{strategyKey}")
    public Mono<ResponseEntity<Map<String, Object>>> disableStorageConfig(@PathVariable String strategyKey) {
        return storageConfigService.enableOrDisableStorageConfig(strategyKey, false)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "code", 200,
                        "message", "存储策略禁用成功"
                ))));
    }
} 