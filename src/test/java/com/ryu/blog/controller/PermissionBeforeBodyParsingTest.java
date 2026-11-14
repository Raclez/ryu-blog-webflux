package com.ryu.blog.controller;

import com.ryu.blog.utils.SaTokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

/**
 * 能力校验必须发生在<b>请求体解析之前</b>。
 *
 * <p><b>它守的是什么</b>：能力校验原先挂在 Controller 方法上的 AOP 切面里，
 * 而 WebFlux 的参数解析（{@code @RequestBody} + {@code @Validated}）
 * 发生在<b>方法被调用之前</b> —— 也就是在切面之前。
 *
 * <p>后果有两个：
 * <ul>
 *   <li>未授权用户带着非法请求体调写接口，拿到的是 <b>400</b> 而不是 <b>403</b> ——
 *       顺带把校验规则（哪些字段必填、什么格式）告诉了不该知道的人；</li>
 *   <li>更实际的是<b>白干活</b>：未授权请求照样触发完整的反序列化与校验，
 *       参数解析出错还会走一遍异常处理。</li>
 * </ul>
 *
 * <p>把能力校验前移到 WebFilter（在建 handler 之前）就能解决：
 * 过滤器阶段只解析路径、不碰请求体，于是 403 一定先于 400。
 *
 * @author ryu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PermissionBeforeBodyParsingTest {

    /**
     * 库里不存在的合成用户 —— 没有任何角色，因此没有任何权限。
     */
    private static final Long NO_PERMISSION_USER_ID = 990202L;

    @Autowired
    private WebTestClient webTestClient;

    private String authToken;

    @BeforeEach
    void setUp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());
        authToken = "Bearer " + SaTokenUtils.login(exchange, NO_PERMISSION_USER_ID).block();
    }

    @Test
    @DisplayName("未授权的写请求带非法请求体 → 403（不是 400）")
    void unauthorizedWriteWithInvalidBodyGets403() {
        // username 是 @NotBlank —— 空值必然触发校验失败。
        webTestClient.post()
                .uri("/users/save")
                .header("Authorization", authToken)
                .bodyValue(Map.of("username", ""))
                .exchange()
                .expectStatus().isForbidden();
    }
}
