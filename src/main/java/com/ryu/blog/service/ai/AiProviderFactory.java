package com.ryu.blog.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.dto.AiProviderConfigValue;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.service.ai.impl.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI提供商工厂
 * 
 * <p>负责根据配置动态创建和管理AI提供商实例。
 * 支持多个提供商和多个模型的可插拔架构。
 * 
 * <p>配置从数据库的 t_sys_config 表中读取，格式：
 * <ul>
 *   <li>ai.provider.{providerName}.enabled - 提供商是否启用</li>
 *   <li>ai.provider.{providerName}.config - 提供商配置（JSON格式）</li>
 *   <li>ai.default.provider - 默认提供商名称</li>
 * </ul>
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderFactory {

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;
    
    /**
     * 提供商实例缓存
     * Key: providerName
     * Value: AiProvider实例
     */
    private final Map<String, AiProvider> providerCache = new ConcurrentHashMap<>();

    /**
     * 获取默认提供商
     * 
     * @return 默认提供商实例
     */
    public Mono<AiProvider> getDefaultProvider() {
        return sysConfigService.getConfigValue("ai.default.provider", "openai")
                .flatMap(this::getProvider);
    }

    /**
     * 根据名称获取提供商
     * 
     * @param providerName 提供商名称（openai, azure, anthropic等）
     * @return 提供商实例
     */
    public Mono<AiProvider> getProvider(String providerName) {
        // 先检查缓存
        if (providerCache.containsKey(providerName)) {
            return Mono.just(providerCache.get(providerName));
        }
        
        // 从数据库加载配置并创建实例
        return loadProviderConfig(providerName)
                .flatMap(config -> {
                    try {
                        AiProvider provider = createProvider(providerName, config);
                        providerCache.put(providerName, provider);
                        return Mono.just(provider);
                    } catch (Exception e) {
                        log.error("创建AI提供商失败: {}", providerName, e);
                        return Mono.error(new RuntimeException("创建AI提供商失败: " + providerName, e));
                    }
                });
    }

    /**
     * 根据请求获取提供商
     * 
     * <p>如果请求中指定了提供商，则使用指定的；否则使用默认提供商。
     * 
     * @param providerName 请求中指定的提供商名称（可为null）
     * @return 提供商实例
     */
    public Mono<AiProvider> getProviderForRequest(String providerName) {
        if (providerName != null && !providerName.isEmpty()) {
            return getProvider(providerName);
        }
        return getDefaultProvider();
    }

    /**
     * 验证提供商配置
     * 
     * @param providerName 提供商名称
     * @param config 配置
     * @return 验证结果
     */
    public Mono<Boolean> validateProviderConfig(String providerName, AiProviderConfigValue config) {
        try {
            AiProvider provider = createProvider(providerName, config);
            return provider.validateConfig(config);
        } catch (Exception e) {
            log.error("验证提供商配置失败: {}", providerName, e);
            return Mono.just(false);
        }
    }

    /**
     * 刷新提供商缓存
     * 
     * <p>当配置更新时调用，清除缓存以便重新加载。
     * 
     * @param providerName 提供商名称，如果为null则清除所有缓存
     */
    public void refreshProvider(String providerName) {
        if (providerName == null) {
            log.info("清除所有AI提供商缓存");
            providerCache.clear();
        } else {
            log.info("清除AI提供商缓存: {}", providerName);
            providerCache.remove(providerName);
        }
    }

    /**
     * 从数据库加载提供商配置
     */
    private Mono<AiProviderConfigValue> loadProviderConfig(String providerName) {
        String configKey = "ai.provider." + providerName;
        
        return sysConfigService.getConfig(configKey)
                .flatMap(configVO -> {
                    // 检查是否启用
                    String enabled = configVO.getConfigValue();
                    if (!"true".equalsIgnoreCase(enabled)) {
                        return Mono.error(new RuntimeException(
                                "AI提供商未启用: " + providerName));
                    }
                    
                    // 从extra字段解析配置
                    String extraJson = configVO.getExtra();
                    if (extraJson == null || extraJson.isEmpty()) {
                        return Mono.error(new RuntimeException(
                                "AI提供商配置不存在（extra字段为空）: " + providerName));
                    }
                    
                    try {
                        AiProviderConfigValue config = objectMapper.readValue(
                                extraJson, AiProviderConfigValue.class);
                        return Mono.just(config);
                    } catch (Exception e) {
                        log.error("解析AI提供商配置失败: {}", providerName, e);
                        return Mono.error(new RuntimeException(
                                "解析AI提供商配置失败: " + providerName, e));
                    }
                })
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "AI提供商配置不存在: " + providerName)));
    }

    /**
     * 创建提供商实例
     */
    private AiProvider createProvider(String providerName, AiProviderConfigValue config) {
        switch (providerName.toLowerCase()) {
            case "openai":
                return new OpenAiProvider(config);
            
            case "azure":
                return new AzureOpenAiProvider(
                        config.getApiKey(),
                        config.getEndpoint(),
                        config.getDeploymentName(),
                        config.getTemperature(),
                        config.getMaxTokens()
                );
            
            case "anthropic":
                return new AnthropicProvider(
                        config.getApiKey(),
                        config.getModelName(),
                        config.getTemperature(),
                        config.getMaxTokens()
                );
            
            case "qwen":
                return new QwenProvider(
                        config.getApiKey(),
                        config.getEndpoint(),
                        config.getModelName(),
                        config.getTemperature(),
                        config.getMaxTokens()
                );
            
            case "gemini":
                return new GeminiProvider(
                        config.getApiKey(),
                        config.getEndpoint(),
                        config.getModelName(),
                        config.getTemperature(),
                        config.getMaxTokens()
                );
            
            case "deepseek":
                return new DeepSeekProvider(
                        config.getApiKey(),
                        config.getEndpoint(),
                        config.getModelName(),
                        config.getTemperature(),
                        config.getMaxTokens()
                );
            
            case "grok":
                return new GrokProvider(
                        config.getApiKey(),
                        config.getEndpoint(),
                        config.getModelName(),
                        config.getTemperature(),
                        config.getMaxTokens()
                );
            
            default:
                throw new IllegalArgumentException("不支持的AI提供商: " + providerName);
        }
    }

    /**
     * 获取所有已启用的提供商名称
     * 
     * @return 提供商名称列表
     */
    public Mono<java.util.List<String>> getEnabledProviders() {
        // 检查所有已知的提供商
        String[] knownProviders = {"openai", "azure", "anthropic", "qwen", "gemini", "deepseek", "grok"};
        
        return reactor.core.publisher.Flux.fromArray(knownProviders)
                .filterWhen(providerName -> {
                    String configKey = "ai.provider." + providerName;
                    return sysConfigService.getConfigValue(configKey, "false")
                            .map("true"::equalsIgnoreCase);
                })
                .collectList();
    }
}
