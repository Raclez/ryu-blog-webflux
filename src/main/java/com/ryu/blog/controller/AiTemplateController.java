package com.ryu.blog.controller;

import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.entity.AiContentTemplate;
import com.ryu.blog.service.AiBlogService;
import com.ryu.blog.service.AiTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * AI模板控制器
 * 
 * <p>提供AI内容模板管理接口。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ai/templates")
@RequiredArgsConstructor
@Validated
@Tag(name = "AI模板", description = "AI内容模板管理接口")
public class AiTemplateController {

    private final AiTemplateService templateService;
    private final AiBlogService aiBlogService;

    @GetMapping
    @Operation(summary = "获取模板列表", description = "获取所有可用的内容模板")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Flux<AiContentTemplate> getTemplates(
            @Parameter(description = "是否只获取启用的模板") @RequestParam(defaultValue = "true") boolean enabledOnly) {
        log.info("获取模板列表: enabledOnly={}", enabledOnly);
        
        if (enabledOnly) {
            return templateService.getEnabledTemplates();
        }
        return templateService.getAllTemplates();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取模板详情", description = "根据ID获取模板详情")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "模板不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<AiContentTemplate> getTemplateById(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        log.info("获取模板详情: id={}", id);
        return templateService.getTemplateById(id);
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "按类型获取模板", description = "根据内容类型获取模板列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Flux<AiContentTemplate> getTemplatesByType(
            @Parameter(description = "内容类型") @PathVariable String type) {
        log.info("按类型获取模板: type={}", type);
        return templateService.getTemplatesByType(type);
    }

    @PostMapping
    @Operation(summary = "创建模板", description = "创建新的内容模板")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功",
                    content = @Content(schema = @Schema(implementation = AiContentTemplate.class))),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<AiContentTemplate> createTemplate(
            @Valid @RequestBody AiContentTemplate template) {
        log.info("创建模板: name={}, type={}", template.getName(), template.getType());
        return templateService.createTemplate(template);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模板", description = "更新现有模板")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功",
                    content = @Content(schema = @Schema(implementation = AiContentTemplate.class))),
            @ApiResponse(responseCode = "404", description = "模板不存在"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<AiContentTemplate> updateTemplate(
            @Parameter(description = "模板ID") @PathVariable Long id,
            @Valid @RequestBody AiContentTemplate template) {
        log.info("更新模板: id={}, name={}", id, template.getName());
        return templateService.updateTemplate(id, template);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板", description = "删除指定模板")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "模板不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Boolean> deleteTemplate(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        log.info("删除模板: id={}", id);
        return templateService.deleteTemplate(id);
    }

    @PostMapping("/{id}/generate")
    @Operation(summary = "使用模板生成", description = "使用指定模板生成内容")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "生成成功",
                    content = @Content(schema = @Schema(implementation = AiGenerationResult.class))),
            @ApiResponse(responseCode = "404", description = "模板不存在"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "429", description = "超出速率限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<AiGenerationResult> generateWithTemplate(
            @Parameter(description = "模板ID") @PathVariable Long id,
            @Parameter(description = "模板变量") @RequestBody Map<String, String> variables,
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("使用模板生成: templateId={}, userId={}", id, userId);
        return aiBlogService.generateWithTemplate(id, variables, userId);
    }
}
