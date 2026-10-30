package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.config.ConditionalOnAiEnabled;
import com.ryu.blog.dto.AiAssistAction;
import com.ryu.blog.dto.AiAssistRequest;
import com.ryu.blog.dto.AiAssistResult;
import com.ryu.blog.service.AiAssistService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.List;

/**
 * AI 辅助编辑控制器
 *
 * <p>面向编辑器的细粒度 AI 动作（润色 / 续写 / 标题候选 / 摘要 / SEO / 翻译 / 解读）。
 * 与 {@link AiBlogController} 的整篇生成互补。
 *
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/ai/assist")
@RequiredArgsConstructor
@Validated
@ConditionalOnAiEnabled
@Tag(name = "AI辅助编辑", description = "编辑器内的细粒度AI能力")
public class AiAssistController {

    private final AiAssistService aiAssistService;

    @PostMapping
    @Operation(summary = "执行辅助编辑", description = "对选中的内容执行润色/续写/标题/摘要/SEO/翻译/解读")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "执行成功",
                    content = @Content(schema = @Schema(implementation = AiAssistResult.class))),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "429", description = "超出配额限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<AiAssistResult>> assist(@Valid @RequestBody AiAssistRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        request.setUserId(userId);
        log.info("收到AI辅助编辑请求: action={}, userId={}", request.getAction(), userId);

        return aiAssistService.assist(request)
                .map(Result::success);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式执行辅助编辑", description = "以 SSE 形式流式返回AI生成内容")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "执行成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "429", description = "超出配额限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Flux<ServerSentEvent<String>> assistStream(@Valid @RequestBody AiAssistRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        request.setUserId(userId);
        log.info("收到AI辅助编辑(流式)请求: action={}, userId={}", request.getAction(), userId);

        return aiAssistService.assistStream(request)
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder()
                        .comment("Stream completed")
                        .build()));
    }

    @GetMapping("/actions")
    @Operation(summary = "获取支持的辅助动作", description = "前端编辑器工具栏据此渲染按钮")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    public Mono<Result<List<AiAssistAction>>> actions() {
        return Mono.just(Result.success(aiAssistService.supportedActions()));
    }
}
