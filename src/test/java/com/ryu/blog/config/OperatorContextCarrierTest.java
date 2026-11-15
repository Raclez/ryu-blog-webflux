package com.ryu.blog.config;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.utils.OperatorContexts;
import com.ryu.blog.utils.SaTokenUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.util.context.Context;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「操作者身份」这个载体的护栏。
 *
 * <p>Service 层现在不接 {@code OperatorContext} 参数了，改成从
 * <b>Reactor Context</b> 里取 —— 这是整套改造的地基：
 * 身份必须<b>跨线程</b>还读得到（Sa-Token 的上下文是纯 ThreadLocal，响应式链上一换线程就丢），
 * 且必须<b>随数据流走</b>（跟「订阅发生在哪一刻」无关）。
 *
 * @author ryu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OperatorContextCarrierTest {

    /** 合成用户 id —— 库里不存在，但 JWT 是自签的，服务端只验签不查库。 */
    private static final Long SYNTHETIC_USER_ID = 990301L;

    /** 一个确定不存在的文章 id —— 用来把「身份没取到」和「文章不存在」区分开。 */
    private static final Long NON_EXISTENT_POST_ID = 987654321L;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Reactor Context 里没有身份时，取到的是匿名（fail-closed，不会误放行）")
    void missingContext_yieldsAnonymous() {
        assertThat(OperatorContexts.from(Context.empty()))
                .isEqualTo(OperatorContext.ANONYMOUS);
        assertThat(OperatorContexts.from(null))
                .isEqualTo(OperatorContext.ANONYMOUS);
    }

    /**
     * 端到端：请求 → 过滤器采集身份 → 写进 Reactor Context → Service 读到身份。
     *
     * <p>用「创建评论」做探针，因为它<b>没有</b> {@code @SaCheckPermission} ——
     * 请求一定能进 Service。而 Service 第一件事就是问「你是谁」
     * （拿不到身份就报「未登录，无法发表评论」）。
     *
     * <p>所以：登录用户发评论，<b>报错必须是「文章不存在」</b>，
     * 而不是「未登录」—— 后者说明身份没送到 Service。
     */
    @Test
    @DisplayName("已登录用户发评论 → 身份到达 Service 层（报的是「文章不存在」而不是「未登录」）")
    void loggedInUser_reachesServiceWithIdentity() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());
        String token = "Bearer " + SaTokenUtils.login(exchange, SYNTHETIC_USER_ID).block();

        byte[] body = webTestClient.post()
                .uri("/comment")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"postId\":" + NON_EXISTENT_POST_ID + ",\"content\":\"身份载体探针\"}")
                .exchange()
                .expectBody()
                .returnResult()
                .getResponseBody();

        String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        assertThat(text)
                .as("身份没送到 Service 层 —— 过滤器没把身份写进 Reactor Context")
                .doesNotContain("未登录");
        assertThat(text)
                .as("请求应当已经走到「文章不存在」这一步，说明身份校验已通过")
                .contains("文章不存在");
    }
}
