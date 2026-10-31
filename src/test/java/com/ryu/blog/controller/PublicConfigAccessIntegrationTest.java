package com.ryu.blog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ryu.blog.constant.PublicConfigKeys;
import com.ryu.blog.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公开配置端点的访问控制集成测试
 *
 * <p>两件事必须同时成立，缺一个这个功能就不安全或不可用：
 * <ol>
 *   <li><b>匿名可读</b> —— 登录页拿不到 token 时也要渲染站点信息；</li>
 *   <li><b>只读到白名单</b> —— 同一个库里有 AI API Key、OSS 的 secret，
 *       一个都不能出现在响应里。</li>
 * </ol>
 *
 * <p>断言"每个返回的键都通过 {@link PublicConfigKeys#isPublic} 判定"而不是
 * 逐个列举具体键，这样测试不依赖开发库里当前存了哪些配置。
 *
 * <p><b>只读</b>：本测试不写库。
 *
 * @author ryu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PublicConfigAccessIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("匿名 GET /sysConfig/public → 200，且只含白名单键")
    void anonymousCanReadOnlyWhitelistedKeys() {
        byte[] body = webTestClient.get()
                .uri("/sysConfig/public")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertTrue(body != null && body.length > 0, "响应体不应为空");

        JsonNode root = JsonUtils.parseTree(new String(body, StandardCharsets.UTF_8));
        JsonNode data = root == null ? null : root.get("data");
        assertTrue(data != null && data.isObject(),
                "data 必须是对象（即使库里没有公开配置也应是 {}），实际: " + root);

        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            assertTrue(PublicConfigKeys.isPublic(entry.getKey()),
                    "非白名单键不得出现在公开响应里: " + entry.getKey());
        }

        // 开发库里确实存在的敏感键，逐条确认没有漏出去
        assertFalse(data.has("security.jwt.secret"));
        assertFalse(data.has("system.storage.oss_secret_key"));
        assertFalse(data.has("ai.default.provider"));
        assertFalse(data.has("ai.provider.openai"));
    }

    @Test
    @DisplayName("匿名 GET /sysConfig/page 仍然 401")
    void adminEndpointStillRequiresLogin() {
        // 公开端点不能顺带把管理端点的门槛降下来
        webTestClient.get()
                .uri("/sysConfig/page")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
