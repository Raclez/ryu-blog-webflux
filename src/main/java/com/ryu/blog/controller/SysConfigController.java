package com.ryu.blog.controller;

import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.SysConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 系统配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/sysConfig")
@RequiredArgsConstructor
@Tag(name = "设置系统配置管理", description = "设置系统配置信息")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    /**
     * 分页获取系统配置信息
     */
    @GetMapping("/page")
    @Operation(summary = "分页获取系统配置信息")
    public Mono<Result<Map<String, Object>>> getSysConfig(
            @RequestParam(required = false) String configKey,
            @RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("请求分页获取系统配置信息，查询条件：key={}", configKey);
        return sysConfigService.getSysConfigPage(configKey, currentPage, pageSize)
                .map(Result::success);
    }

    /**
     * 根据ID获取配置信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取配置信息")
    public Mono<Result<SysConfigVO>> getConfigById(
            @Parameter(description = "配置ID") @PathVariable("id") Long id) {
        return sysConfigService.getConfigById(id)
                .map(Result::success)
                .defaultIfEmpty(Result.fail("配置不存在"));
    }

    /**
     * 添加配置
     */
    @PostMapping("/save")
    @Operation(summary = "添加系统配置信息")
    public Mono<Result<SysConfigVO>> addConfig(@Valid @RequestBody SysConfigDTO configDTO) {
        log.info("请求添加系统配置，配置信息：{}", configDTO);
        return sysConfigService.addConfig(configDTO)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("添加系统配置失败，配置信息：{}", configDTO, e);
                    return Mono.just(Result.fail(e.getMessage()));
                });
    }

    /**
     * 修改系统配置信息
     */
    @PutMapping("/edit")
    @Operation(summary = "修改系统配置信息")
    public Mono<Result<SysConfigVO>> updateConfig(@Valid @RequestBody SysConfigUpdateDTO configDTO) {
        log.info("请求修改系统配置，配置ID：{}", configDTO.getId());
        return sysConfigService.updateConfig(configDTO)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("修改系统配置失败，配置ID：{}", configDTO.getId(), e);
                    return Mono.just(Result.fail(e.getMessage()));
                });
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统配置信息")
    public Mono<Result<String>> deleteConfig(
            @Parameter(description = "配置ID") @PathVariable("id") Long id) {
        log.info("请求删除系统配置，配置ID：{}", id);
        return sysConfigService.deleteConfig(id)
                .map(result -> result ? Result.success("删除成功") : Result.fail("删除失败，可能是系统内置配置"));
    }
} 