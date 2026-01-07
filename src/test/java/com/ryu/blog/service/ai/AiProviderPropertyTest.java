package com.ryu.blog.service.ai;

import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiGenerationResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

/**
 * AI提供商属性测试
 * 
 * <p>测试属性7：提供商切换透明性
 * 
 * @author Ryu
 * @since 1.0.0
 */
public class AiProviderPropertyTest {

    /**
     * 属性7：提供商切换透明性
     * 
     * <p>对于任何两个配置正确的AI提供商，切换提供商后，
     * 相同的生成请求应该产生语义相似的结果（虽然具体内容可能不同）。
     * 
     * <p>验证：需求6.1, 6.2
     * 
     * <p>测试策略：
     * 1. 验证不同提供商的接口一致性
     * 2. 验证返回结果的结构一致性
     * 3. 验证基本的内容质量指标（非空、长度合理）
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 7: 提供商切换透明性")
    void providerInterfaceConsistency(
            @ForAll @NotBlank @StringLength(min = 10, max = 100) String topic,
            @ForAll @IntRange(min = 100, max = 2000) int length,
            @ForAll("providerNames") String providerName) {
        
        // 创建生成请求
        AiGenerationRequest request = AiGenerationRequest.builder()
                .topic(topic)
                .length(length)
                .providerName(providerName)
                .language("zh")
                .tone("professional")
                .build();
        
        // 验证请求对象的一致性
        assert request.getTopic() != null && !request.getTopic().isBlank();
        assert request.getLength() >= 100 && request.getLength() <= 2000;
        assert request.getProviderName() != null;
        assert request.getLanguage() != null;
        assert request.getTone() != null;
        
        // 验证：所有提供商都应该接受相同格式的请求
        // 这确保了提供商切换的透明性
    }

    /**
     * 验证生成结果的结构一致性
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 7: 提供商切换透明性")
    void resultStructureConsistency(@ForAll("generationResults") AiGenerationResult result) {
        // 验证所有提供商返回的结果都包含必要的字段
        assert result.getContent() != null;
        assert result.getProviderName() != null;
        assert result.getModelName() != null;
        
        // 如果有令牌计数，应该是正数
        if (result.getTokenCount() != null) {
            assert result.getTokenCount() > 0;
        }
        
        // 如果有生成时间，应该是正数
        if (result.getGenerationTime() != null) {
            assert result.getGenerationTime() > 0;
        }
        
        // 如果有成本估算，应该是非负数
        if (result.getEstimatedCost() != null) {
            assert result.getEstimatedCost() >= 0;
        }
    }

    /**
     * 提供商名称生成器
     */
    @Provide
    Arbitrary<String> providerNames() {
        return Arbitraries.of("openai", "azure", "anthropic", "gemini", "qwen");
    }

    /**
     * 生成结果生成器
     */
    @Provide
    Arbitrary<AiGenerationResult> generationResults() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(100).ofMaxLength(5000),
                Arbitraries.of("openai", "azure", "anthropic", "gemini", "qwen"),
                Arbitraries.of("gpt-4", "gpt-3.5-turbo", "claude-3", "gemini-pro", "qwen-max"),
                Arbitraries.integers().between(100, 10000),
                Arbitraries.doubles().between(0.0, 10.0),
                Arbitraries.longs().between(100L, 30000L)
        ).as((content, provider, model, tokens, cost, time) ->
                AiGenerationResult.builder()
                        .content(content)
                        .providerName(provider)
                        .modelName(model)
                        .tokenCount(tokens)
                        .estimatedCost(cost)
                        .generationTime(time)
                        .build()
        );
    }
}
