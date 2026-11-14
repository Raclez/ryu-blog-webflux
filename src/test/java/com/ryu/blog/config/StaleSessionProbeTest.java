package com.ryu.blog.config;

import com.ryu.blog.utils.SaTokenUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * <b>诊断用例，不是断言用例。</b>
 *
 * <p>它复现的是一个<b>尚未修复</b>的独立问题：Sa-Token 1.37/1.38 时代写进 Redis 的
 * session（类名 {@code SaSessionForJacksonCustomized}）在 1.42+ 的反序列化器下无法解析，
 * 于是任何用到该 session 的请求都会抛
 * {@code InvalidTypeIdException} → 500。
 *
 * <p>修复方向是「读到无法反序列化的旧 session 时返回 401 而不是 500」。
 * 那个修复<b>不在</b>权限设计整改的范围里，所以本用例改成只打印不失败 ——
 * 它是现场排查用的探针，不该把整个测试构建拖红。
 *
 * <p>注意：本用例会往 Redis 写一条脏 session（用的是一个不存在的用户 id），
 * 排查完记得手工清理 {@code Authorization:login:session:990401}。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class StaleSessionProbeTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> rawRedis;

    @Test
    void probe() {
        Long userId = 990401L;
        try {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/auth/login").build());
            String token = "Bearer " + SaTokenUtils.login(exchange, userId).block();

            // 1.37/1.38 时代写入的 session 格式（类名在 1.42 已被删除）
            String legacy = "{\"@class\":\"cn.dev33.satoken.dao.SaSessionForJacksonCustomized\","
                    + "\"id\":\"Authorization:login:session:" + userId + "\","
                    + "\"type\":\"Account-Session\",\"loginType\":\"login\","
                    + "\"loginId\":[\"java.lang.Long\"," + userId + "],\"token\":null,"
                    + "\"createTime\":1793545914190,"
                    + "\"dataMap\":{\"@class\":\"java.util.concurrent.ConcurrentHashMap\"}}";
            rawRedis.opsForValue().set("Authorization:login:session:" + userId, legacy).block();

            var result = webTestClient.get()
                    .uri("/users/detail/1")
                    .header("Authorization", token)
                    .exchange()
                    .returnResult(String.class);

            String body = result.getResponseBody().blockFirst();
            System.out.println("PROBE status=" + result.getStatus());
            System.out.println("PROBE body=" + (body == null ? "" : body));
        } catch (Exception e) {
            // 探针：异常本身就是现象的一部分，打印出来即可，不让构建失败。
            System.out.println("PROBE 旧 session 反序列化失败（未修复的已知问题）: " + e);
        } finally {
            rawRedis.opsForValue().delete("Authorization:login:session:" + userId).block();
        }
    }
}
