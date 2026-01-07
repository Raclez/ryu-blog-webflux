package com.ryu.blog.controller;

import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/sysConfig")
@RequiredArgsConstructor
@Tag(name = "系统配置管理", description = "系统配置信息的增删改查")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    /**
     * 分页获取系统配置信息
     */
    @GetMapping("/page")
    @Operation(summary = "分页获取系统配置信息", description = "支持按配置键模糊查询")
    public Mono<Result<PageResult<SysConfig>>> getSysConfig(
            @Parameter(description = "配置键（模糊查询）") @RequestParam(required = false) String configKey,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") @Min(1) int currentPage,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        log.info("分页获取系统配置，查询条件：key={}, page={}, size={}", configKey, currentPage, pageSize);
        return sysConfigService.getSysConfigPage(configKey, currentPage, pageSize)
                .map(Result::success);
    }

    /**
     * 根据ID获取配置信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取配置信息")
    public Mono<Result<SysConfig>> getConfigById(
            @Parameter(description = "配置ID") @PathVariable("id") Long id) {
        log.info("根据ID获取配置信息，id={}", id);
        return sysConfigService.getConfigById(id)
                .map(Result::success)
                .defaultIfEmpty(Result.fail("配置不存在"));
    }

    /**
     * 根据配置键获取配置信息
     */
    @GetMapping("/key/{configKey}")
    @Operation(summary = "根据配置键获取配置信息")
    public Mono<Result<SysConfig>> getConfigByKey(
            @Parameter(description = "配置键") @PathVariable("configKey") String configKey) {
        log.info("根据配置键获取配置信息，key={}", configKey);
        return sysConfigService.getConfig(configKey)
                .map(Result::success)
                .defaultIfEmpty(Result.fail("配置不存在"));
    }

    /**
     * 添加配置
     */
    @PostMapping("/save")
    @Operation(summary = "添加系统配置信息", description = "配置键格式：分组.子分组.配置名")
    public Mono<Result<SysConfig>> addConfig(@Valid @RequestBody SysConfigDTO configDTO) {
        log.info("添加系统配置，配置信息：{}", configDTO);
        return sysConfigService.addConfig(configDTO)
                .map(Result::success);
    }

    /**
     * 修改系统配置信息
     */
    @PutMapping("/edit")
    @Operation(summary = "修改系统配置信息")
    public Mono<Result<SysConfig>> updateConfig(@Valid @RequestBody SysConfigUpdateDTO configDTO) {
        log.info("修改系统配置，配置ID：{}", configDTO.getId());
        return sysConfigService.updateConfig(configDTO)
                .map(Result::success);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统配置信息", description = "删除配置并返回被删除的配置信息")
    public Mono<Result<SysConfig>> deleteConfig(
            @Parameter(description = "配置ID") @PathVariable("id") Long id) {
        log.info("删除系统配置，配置ID：{}", id);
        return sysConfigService.deleteConfig(id)
                .map(config -> {
                    log.info("成功删除配置 - id: {}, configKey: {}", config.getId(), config.getConfigKey());
                    return Result.success(config);
                });
    }

    /**
     * 批量获取配置值
     */
    @PostMapping("/batch/get")
    @Operation(summary = "批量获取配置值", description = "根据配置键列表批量获取配置值")
    public Mono<Result<Map<String, String>>> batchGetConfigValues(
            @Parameter(description = "配置键列表") @RequestBody @NotEmpty(message = "配置键列表不能为空") List<String> keys) {
        log.info("批量获取配置值，keys={}", keys);
        return sysConfigService.batchGetConfigValues(keys)
                .map(Result::success);
    }

    /**
     * 批量更新配置
     */
    @PutMapping("/batch/update")
    @Operation(summary = "批量更新配置", description = "根据配置键值对批量更新配置")
    public Mono<Result<Boolean>> batchUpdateConfigs(
            @Parameter(description = "配置键值对") @RequestBody @NotEmpty(message = "配置数据不能为空") Map<String, String> configs) {
        log.info("批量更新配置，count={}", configs.size());
        return sysConfigService.batchUpdateConfig(configs)
                .map(Result::success);
    }

    /**
     * 获取配置分组列表
     */
    @GetMapping("/groups")
    @Operation(summary = "获取配置分组列表", description = "获取所有配置的分组和子分组信息")
    public Mono<Result<Map<String, Object>>> getConfigGroups() {
        log.info("获取配置分组列表");
        return sysConfigService.getConfigGroups()
                .map(Result::success);
    }

    /**
     * 根据分组前缀获取配置列表
     */
    @GetMapping("/list")
    @Operation(summary = "根据分组前缀获取配置列表")
    public Mono<Result<List<SysConfig>>> getConfigList(
            @Parameter(description = "分组前缀") @RequestParam(required = false) String groupPrefix) {
        log.info("获取配置列表，groupPrefix={}", groupPrefix);
        return sysConfigService.getConfigList(groupPrefix)
                .collectList()
                .map(Result::success);
    }

    /**
     * 搜索配置
     */
    @GetMapping("/search")
    @Operation(summary = "搜索配置", description = "根据配置键或备注模糊搜索")
    public Mono<Result<List<SysConfig>>> searchConfig(
            @Parameter(description = "搜索关键字") @RequestParam String keyword) {
        log.info("搜索配置，keyword={}", keyword);
        return sysConfigService.searchConfig(keyword)
                .collectList()
                .map(Result::success);
    }

    /**
     * 清除所有配置缓存
     */
    @DeleteMapping("/cache/clear")
    @Operation(summary = "清除所有配置缓存", description = "清除系统配置相关的所有缓存")
    public Mono<Result<Boolean>> clearCache() {
        log.info("清除所有配置缓存");
        return sysConfigService.clearAllCache()
                .map(Result::success);
    }
}
