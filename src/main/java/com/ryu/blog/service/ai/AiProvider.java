package com.ryu.blog.service.ai;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.dto.AiModelInfo;
import com.ryu.blog.dto.AiProviderConfigValue;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * AI提供商接口
 * 
 * <p>定义了AI提供商的核心功能，包括：
 * <ul>
 *   <li>非流式内容生成</li>
 *   <li>流式内容生成</li>
 *   <li>模型信息查询</li>
 *   <li>提供商配置验证</li>
 * </ul>
 * 
 * <p>所有AI提供商实现类都必须实现此接口，以确保统一的调用方式。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public interface AiProvider {

    /**
     * 获取提供商名称
     * 
     * @return 提供商名称（如：openai, azure, anthropic）
     */
    String getProviderName();

    /**
     * 非流式生成内容
     * 
     * <p>同步生成完整的内容，适用于对实时性要求不高的场景。
     * 
     * @param request 生成请求
     * @return 生成结果的Mono包装
     */
    Mono<AiGenerationResult> generate(AiGenerationRequest request);

    /**
     * 流式生成内容
     * 
     * <p>以流的方式逐步返回生成的内容，适用于需要实时反馈的场景。
     * 每个元素代表生成内容的一个片段。
     * 
     * @param request 生成请求
     * @return 内容片段的Flux流
     */
    Flux<String> generateStream(AiGenerationRequest request);

    /**
     * 获取支持的模型列表
     * 
     * @return 模型信息列表的Mono包装
     */
    Mono<List<AiModelInfo>> getSupportedModels();

    /**
     * 验证提供商配置是否有效
     * 
     * <p>检查API密钥、端点等配置是否正确。
     * 通过实际调用API来验证配置的有效性。
     * 
     * @param config 提供商配置
     * @return 配置有效返回true，否则返回false
     */
    Mono<Boolean> validateConfig(AiProviderConfigValue config);

    /**
     * 检查提供商是否可用
     * 
     * <p>检查提供商服务是否正常运行。
     * 
     * @return 可用返回true，否则返回false
     */
    Mono<Boolean> isAvailable();
}
