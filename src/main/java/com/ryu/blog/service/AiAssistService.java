package com.ryu.blog.service;

import com.ryu.blog.dto.AiAssistAction;
import com.ryu.blog.dto.AiAssistRequest;
import com.ryu.blog.dto.AiAssistResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * AI 辅助编辑服务
 *
 * <p>把 AI 从「另一个页面的独立功能」变成「编辑器里的一个动作」。
 * 与 {@code AiBlogService}（整篇生成）互补，共享同一套 Provider、配额与用量统计。
 *
 * @author ryu
 */
public interface AiAssistService {

    /**
     * 执行一次辅助编辑
     *
     * @param request 辅助请求
     * @return 辅助结果
     */
    Mono<AiAssistResult> assist(AiAssistRequest request);

    /**
     * 流式执行一次辅助编辑
     *
     * @param request 辅助请求
     * @return 内容片段流
     */
    Flux<String> assistStream(AiAssistRequest request);

    /**
     * 支持的动作清单
     *
     * @return 动作说明列表
     */
    List<AiAssistAction> supportedActions();
}
