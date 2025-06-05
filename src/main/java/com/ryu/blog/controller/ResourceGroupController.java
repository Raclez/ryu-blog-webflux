package com.ryu.blog.controller;

import com.ryu.blog.dto.ResourceGroupCreateDTO;
import com.ryu.blog.dto.ResourceGroupFileDTO;
import com.ryu.blog.dto.ResourceGroupQueryDTO;
import com.ryu.blog.dto.ResourceGroupUpdateDTO;
import com.ryu.blog.service.ResourceGroupService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.utils.SaTokenUtils;
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.ResourceGroupVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 资源组管理控制器
 *
 * @author ryu
 */
@RestController
@RequestMapping("/resourceGroup")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "资源组管理", description = "提供资源组的创建、查询、修改、删除等功能")
@Validated
public class ResourceGroupController {

    private final ResourceGroupService resourceGroupService;

    /**
     * 创建资源组
     *
     * @param dto 创建DTO
     * @param exchange 服务器交换器
     * @return 创建结果
     */
    @PostMapping("/save")
    @Operation(summary = "创建资源组", description = "创建新的资源组")
    public Mono<Result<Void>> createResourceGroup(@Valid @RequestBody ResourceGroupCreateDTO dto, ServerWebExchange exchange) {
        return resourceGroupService.createResourceGroup(dto, exchange)
                .then(Mono.just(Result.<Void>success()))
                .onErrorResume(e -> {
                    log.error("创建资源组失败", e);
                    return Mono.just(Result.<Void>error(e.getMessage()));
                });
    }

    /**
     * 更新资源组
     *
     * @param dto 更新DTO
     * @return 更新结果
     */
    @PutMapping("/edit")
    @Operation(summary = "更新资源组", description = "更新资源组信息")
    public Mono<Result<Void>> updateResourceGroup(@Valid @RequestBody ResourceGroupUpdateDTO dto) {
        return resourceGroupService.updateResourceGroup(dto)
                .then(Mono.just(Result.<Void>success()))
                .onErrorResume(e -> {
                    log.error("更新资源组失败", e);
                    return Mono.just(Result.<Void>error(e.getMessage()));
                });
    }

    /**
     * 删除资源组
     *
     * @param id 资源组ID
     * @return 删除结果
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除资源组", description = "根据ID删除资源组")
    public Mono<Result<Void>> deleteResourceGroup(@PathVariable Long id) {
        return resourceGroupService.deleteResourceGroup(id)
                .then(Mono.just(Result.<Void>success()))
                .onErrorResume(e -> {
                    log.error("删除资源组失败", e);
                    return Mono.just(Result.<Void>error(e.getMessage()));
                });
    }

    /**
     * 获取当前用户的资源组
     *
     * @param exchange 服务器交换器
     * @return 资源组列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取当前用户资源组", description = "获取当前登录用户的所有资源组")
    public Mono<Result<List<ResourceGroupVO>>> getCurrentUserResourceGroups(ServerWebExchange exchange) {
        return SaTokenUtils.exec(exchange, StpUtil::getLoginIdAsLong)
                .flatMap(userId -> 
                    resourceGroupService.getUserResourceGroups(userId)
                            .collectList()
                            .map(Result::success)
                )
                .onErrorResume(e -> {
                    log.error("获取当前用户资源组失败", e);
                    return Mono.just(Result.<List<ResourceGroupVO>>error(e.getMessage()));
                });
    }

    /**
     * 添加文件到资源组
     *
     * @param dto 资源组文件DTO
     * @return 操作结果
     */
    @PostMapping("/file/add")
    @Operation(summary = "添加文件到资源组", description = "将文件添加到指定资源组")
    public Mono<Result<Void>> addFilesToGroup(@Valid @RequestBody ResourceGroupFileDTO dto) {
        return resourceGroupService.addFilesToGroup(dto)
                .then(Mono.just(Result.<Void>success()))
                .onErrorResume(e -> {
                    log.error("添加文件到资源组失败", e);
                    return Mono.just(Result.<Void>error(e.getMessage()));
                });
    }

    /**
     * 从资源组移除文件
     *
     * @param dto 资源组文件DTO
     * @return 操作结果
     */
    @PostMapping("/file/remove")
    @Operation(summary = "从资源组移除文件", description = "从指定资源组移除文件")
    public Mono<Result<Void>> removeFilesFromGroup(@Valid @RequestBody ResourceGroupFileDTO dto) {
        return resourceGroupService.removeFilesFromGroup(dto)
                .then(Mono.just(Result.<Void>success()))
                .onErrorResume(e -> {
                    log.error("从资源组移除文件失败", e);
                    return Mono.just(Result.<Void>error(e.getMessage()));
                });
    }

    /**
     * 获取资源组中的文件ID列表，当不传入groupId时查询所有文件
     *
     * @param queryDTO 查询参数
     * @return 文件ID列表分页结果
     */
    @GetMapping("/files")
    @Operation(summary = "获取资源组文件列表", description = "分页获取资源组中的文件ID列表，不传groupId则查询所有文件")
    public Mono<Result<PageResult<Long>>> getGroupFileIds(@Valid ResourceGroupQueryDTO queryDTO) {
        return resourceGroupService.getGroupFileIds(queryDTO)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("获取资源组文件列表失败", e);
                    return Mono.just(Result.<PageResult<Long>>error(e.getMessage()));
                });
    }

    /**
     * 检查资源组名是否存在
     *
     * @param groupName 资源组名
     * @param excludeId 排除的资源组ID（更新时使用）
     * @return 是否存在
     */
    @GetMapping("/check")
    @Operation(summary = "检查资源组名是否存在", description = "检查指定的资源组名是否已存在")
    public Mono<Result<Boolean>> checkGroupNameExists(
            @RequestParam String groupName,
            @RequestParam(required = false) Long excludeId) {
        return resourceGroupService.checkGroupNameExists(groupName, excludeId)
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("检查资源组名是否存在失败", e);
                    return Mono.just(Result.<Boolean>error(e.getMessage()));
                });
    }

    /**
     * 获取文件所属的资源组
     *
     * @param fileId 文件ID
     * @return 资源组列表
     */
    @GetMapping("/file/{fileId}")
    @Operation(summary = "获取文件所属资源组", description = "获取指定文件所属的所有资源组")
    public Mono<Result<List<ResourceGroupVO>>> getFileResourceGroups(@PathVariable Long fileId) {
        return resourceGroupService.getFileResourceGroups(fileId)
                .collectList()
                .map(Result::success)
                .onErrorResume(e -> {
                    log.error("获取文件所属资源组失败", e);
                    return Mono.just(Result.<List<ResourceGroupVO>>error(e.getMessage()));
                });
    }
} 