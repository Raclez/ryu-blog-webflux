package com.ryu.blog.service;

import com.ryu.blog.dto.ArticlePublishRequest;
import com.ryu.blog.dto.ArticlePublishResult;
import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.enums.TaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务请求体的反序列化
 *
 * <p>任务在 DB 里存的是 JSON 字符串，执行前必须还原成 handler 声明的类型。
 * 此前这一步在执行器里按任务类型硬编码分支（只有 AI_GENERATION 有分支），
 * 其余类型一律退化成 {@code Map} —— handler 拿到的根本不是自己声明的类型，
 * 泛型擦除之后在运行期才炸成 {@code ClassCastException}。
 */
class TaskHandlerRegistryDeserializeTest {

    @Test
    @DisplayName("声明了请求类型的 handler → 还原成强类型对象")
    void deserializesToDeclaredType() {
        TaskHandlerRegistry registry = registry(new TypedArticleHandler());

        String json = "{\"postId\":42,\"publishTime\":\"2026-10-29T20:00:00\"}";
        Object request = registry.deserialize(TaskType.ARTICLE_PUBLISH, json);

        assertThat(request).isInstanceOf(ArticlePublishRequest.class);
        ArticlePublishRequest typed = (ArticlePublishRequest) request;
        assertThat(typed.getPostId()).isEqualTo(42L);
        assertThat(typed.getPublishTime()).isNotNull();
    }

    @Test
    @DisplayName("没声明类型的 handler → 退化成 Map（旧行为不变）")
    void fallsBackToMapWhenTypeNotDeclared() {
        TaskHandlerRegistry registry = registry(new UntypedHandler());

        Object request = registry.deserialize(TaskType.AI_GENERATION, "{\"mode\":\"blog\"}");

        assertThat(request).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) request;
        assertThat(map).containsEntry("mode", "blog");
    }

    @Test
    @DisplayName("AI 任务也能按声明的类型还原")
    void deserializesAiRequest() {
        TaskHandlerRegistry registry = registry(new TypedAiHandler());

        Object request = registry.deserialize(TaskType.AI_GENERATION,
                "{\"mode\":\"free\",\"prompt\":\"写一篇关于WebFlux的教程\"}");

        assertThat(request).isInstanceOf(AiGenerationRequest.class);
        assertThat(((AiGenerationRequest) request).getPrompt()).isEqualTo("写一篇关于WebFlux的教程");
    }

    @Test
    @DisplayName("没有 handler 的任务类型 → 明确报错，而不是返回 null 让下游 NPE")
    void unknownTaskTypeFails() {
        // 注册表里只有 ARTICLE_PUBLISH 的 handler
        TaskHandlerRegistry registry = registry(new TypedArticleHandler());

        assertThatThrownBy(() -> registry.deserialize(TaskType.AI_GENERATION, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler found");
    }

    @Test
    @DisplayName("JSON 坏了 → 明确报错，而不是把 null 交给 handler")
    void brokenJsonFails() {
        TaskHandlerRegistry registry = registry(new TypedArticleHandler());

        assertThatThrownBy(() -> registry.deserialize(TaskType.ARTICLE_PUBLISH, "这不是 JSON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to deserialize");
    }

    // =====================================================================
    // 测试用的假 handler
    // =====================================================================

    private TaskHandlerRegistry registry(TaskHandler<?, ?>... handlers) {
        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(handlers));
        registry.init();
        return registry;
    }

    /**
     * 声明了请求类型的 handler
     */
    static class TypedArticleHandler implements TaskHandler<ArticlePublishRequest, ArticlePublishResult> {
        @Override
        public TaskType getTaskType() {
            return TaskType.ARTICLE_PUBLISH;
        }

        @Override
        public Class<ArticlePublishRequest> getRequestType() {
            return ArticlePublishRequest.class;
        }

        @Override
        public reactor.core.publisher.Mono<ArticlePublishResult> execute(ArticlePublishRequest request) {
            return reactor.core.publisher.Mono.empty();
        }
    }

    /**
     * AI 任务：声明了请求类型
     */
    static class TypedAiHandler implements TaskHandler<AiGenerationRequest, Object> {
        @Override
        public TaskType getTaskType() {
            return TaskType.AI_GENERATION;
        }

        @Override
        public Class<AiGenerationRequest> getRequestType() {
            return AiGenerationRequest.class;
        }

        @Override
        public reactor.core.publisher.Mono<Object> execute(AiGenerationRequest request) {
            return reactor.core.publisher.Mono.empty();
        }
    }

    /**
     * 不声明请求类型的 handler（默认实现）
     */
    static class UntypedHandler implements TaskHandler<Map<String, Object>, Object> {
        @Override
        public TaskType getTaskType() {
            return TaskType.AI_GENERATION;
        }

        @Override
        public reactor.core.publisher.Mono<Object> execute(Map<String, Object> request) {
            return reactor.core.publisher.Mono.empty();
        }
    }
}
