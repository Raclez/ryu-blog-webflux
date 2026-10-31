package com.ryu.blog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ryu.blog.entity.SysConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * 类型化读取的语义测试
 *
 * <p>这些方法是 default 实现，只负责「把字符串翻译成类型」，
 * 所以用 {@code CALLS_REAL_METHODS} 的 mock：default 方法走真实逻辑，
 * 只有抽象方法 {@code getConfigValue} / {@code getConfig} 被替换成固定返回。
 *
 * <p>重点锁死两条容易退化的语义：<b>键缺失/空值必须回落到默认值</b>，
 * <b>非法值不能抛异常</b>（一条写坏的配置不该让调用方挂掉）。
 *
 * @author ryu
 */
class SysConfigServiceTypedAccessorTest {

    private SysConfigService serviceReturning(String value) {
        SysConfigService service = Mockito.mock(SysConfigService.class,
                Mockito.CALLS_REAL_METHODS);
        // getConfigValue 是抽象方法，CALLS_REAL_METHODS 下返回 null，必须显式打桩
        doReturn(Mono.justOrEmpty(value)).when(service).getConfigValue(Mockito.anyString(), Mockito.anyString());
        return service;
    }

    private SysConfigService serviceReturningConfig(SysConfig config) {
        SysConfigService service = Mockito.mock(SysConfigService.class,
                Mockito.CALLS_REAL_METHODS);
        doReturn(Mono.justOrEmpty(config)).when(service).getConfig(Mockito.anyString());
        return service;
    }

    @Test
    @DisplayName("布尔：大小写不敏感，非法值回落默认")
    void booleanParsing() {
        StepVerifier.create(serviceReturning("TRUE").getBoolean("k", false))
                .expectNext(true).verifyComplete();
        StepVerifier.create(serviceReturning(" true ").getBoolean("k", false))
                .expectNext(true).verifyComplete();
        StepVerifier.create(serviceReturning("yes").getBoolean("k", true))
                .expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("布尔：键缺失或空值回落默认")
    void booleanFallsBack() {
        StepVerifier.create(serviceReturning(null).getBoolean("k", true))
                .expectNext(true).verifyComplete();
        StepVerifier.create(serviceReturning("").getBoolean("k", true))
                .expectNext(true).verifyComplete();
        StepVerifier.create(serviceReturning("   ").getBoolean("k", false))
                .expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("整数：正常解析，非法值回落默认")
    void intParsing() {
        StepVerifier.create(serviceReturning("20").getInt("k", 10))
                .expectNext(20).verifyComplete();
        StepVerifier.create(serviceReturning("-3").getInt("k", 10))
                .expectNext(-3).verifyComplete();
        StepVerifier.create(serviceReturning("twenty").getInt("k", 10))
                .expectNext(10).verifyComplete();
        StepVerifier.create(serviceReturning(null).getInt("k", 10))
                .expectNext(10).verifyComplete();
    }

    @Test
    @DisplayName("长整数：正常解析，非法值回落默认")
    void longParsing() {
        StepVerifier.create(serviceReturning("86400").getLong("k", 1L))
                .expectNext(86_400L).verifyComplete();
        StepVerifier.create(serviceReturning("x").getLong("k", 1L))
                .expectNext(1L).verifyComplete();
    }

    @Test
    @DisplayName("JSON：合法内容解析成树，非法内容返回空而不是报错")
    void jsonParsing() {
        Mono<JsonNode> ok = serviceReturning("{\"apiKey\":\"sk-x\",\"temperature\":0.7}")
                .getJsonConfig("k");
        StepVerifier.create(ok)
                .assertNext(node -> {
                    org.junit.jupiter.api.Assertions.assertEquals("sk-x", node.get("apiKey").asText());
                    org.junit.jupiter.api.Assertions.assertEquals(0.7, node.get("temperature").asDouble(), 1e-9);
                })
                .verifyComplete();

        StepVerifier.create(serviceReturning("{not-json").getJsonConfig("k"))
                .verifyComplete();
        StepVerifier.create(serviceReturning("").getJsonConfig("k"))
                .verifyComplete();
    }

    @Test
    @DisplayName("extra：读配置的 extra 字段并解析成树")
    void extraParsing() {
        SysConfig config = SysConfig.builder()
                .configKey("ai.provider.openai")
                .configValue("true")
                .extra("{\"modelName\":\"gpt-4\"}")
                .build();

        StepVerifier.create(serviceReturningConfig(config).getExtraConfig("ai.provider.openai"))
                .assertNext(node -> org.junit.jupiter.api.Assertions.assertEquals(
                        "gpt-4", node.get("modelName").asText()))
                .verifyComplete();

        SysConfig noExtra = SysConfig.builder().configKey("k").build();
        StepVerifier.create(serviceReturningConfig(noExtra).getExtraConfig("k"))
                .verifyComplete();
    }
}
