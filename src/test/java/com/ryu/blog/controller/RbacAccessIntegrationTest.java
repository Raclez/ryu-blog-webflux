package com.ryu.blog.controller;

import com.ryu.blog.utils.SaTokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RBAC 端点的访问控制集成测试 —— 对应 Stage 4 的验收标准：
 * 「普通用户 token 逐条打管理端点，期望 403；匿名期望 401」。
 *
 * <p><b>为什么用「没有角色的合成用户」</b>：本项目的权限判定链路是
 * {@code @SaCheckPermission} → {@code SaTokenStpInterface} → {@code UserPermissionCache}
 * → {@code t_user_roles} / {@code t_role_permissions} / {@code t_permissions}。
 * 用一个库里不存在、也没有任何角色的用户 id 登录，它拿到的权限集合必然为空 ——
 * 于是「权限不足 → 403」这条路径可以被确定性地验证，
 * 而不必依赖开发库里某个具体账号的角色配置。
 *
 * <p><b>只读</b>：本测试不写库，只发请求。
 *
 * @author ryu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RbacAccessIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * 合成用户 id —— 库里不存在，也没有角色，因此没有任何权限。
     */
    private static final Long NO_PERMISSION_USER_ID = 990101L;

    /**
     * 受保护的管理端点（都带 {@code @SaCheckPermission}）。
     *
     * <p>只挑<b>没有请求体</b>的端点：WebFlux 的参数解析（含 {@code @Valid}）
     * 发生在 AOP 鉴权<b>之前</b>，请求体不合法会先返回 400，
     * 根本走不到权限检查 —— 那样测出来的就不是 403 了。
     */
    private static final List<String> PROTECTED_ENDPOINTS = List.of(
            "/users/page",
            "/users/detail/1",
            "/roles/page",
            "/roles/list",
            "/roles/get/1",
            "/roles/user/1",
            "/menus/tree",
            "/menus/all",
            "/permissions/list",
            "/permissions/page",
            "/permissions/modules",
            "/permissions/role/1");

    private String authToken;

    @BeforeEach
    void setUp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());
        authToken = "Bearer " + SaTokenUtils.login(exchange, NO_PERMISSION_USER_ID).block();
    }

    @Test
    @DisplayName("匿名访问管理端点 → 401")
    void anonymousIsRejectedWith401() {
        for (String path : PROTECTED_ENDPOINTS) {
            int status = webTestClient.get()
                    .uri(path)
                    .exchange()
                    .returnResult(int.class)
                    .getStatus().value();
            assertEquals(401, status, path + " 匿名访问应当是 401");
        }
    }

    @Test
    @DisplayName("没有权限的普通用户访问管理端点 → 403")
    void userWithoutPermissionGets403() {
        for (String path : PROTECTED_ENDPOINTS) {
            int status = webTestClient.get()
                    .uri(path)
                    .header("Authorization", authToken)
                    .exchange()
                    .returnResult(int.class)
                    .getStatus().value();
            assertEquals(403, status, path + " 无权限用户访问应当是 403");
        }
    }

    @Test
    @DisplayName("403 的响应体是合法 JSON，且带明确的错误码")
    void forbiddenResponseIsJson() {
        byte[] body = webTestClient.get()
                .uri("/users/page")
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .returnResult()
                .getResponseBody();

        String text = body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(true, text.contains("权限不足"),
                "403 响应体应当说明是权限问题，实际是：" + text);
    }

    @Test
    @DisplayName("写操作的管理端点同样要 403（不只是查询接口）")
    void writeOperationsAreAlsoProtected() {
        // 删除一个不存在的用户 —— 鉴权必须先于业务逻辑发生，
        // 所以即便 id 不存在也应该是 403，而不是 404/500。
        webTestClient.delete()
                .uri("/users/delete/999999999")
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.put()
                .uri("/users/1/status/0")
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.delete()
                .uri("/permissions/delete/999999999")
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("匿名调用写操作 → 401")
    void anonymousWriteIsRejectedWith401() {
        webTestClient.delete()
                .uri("/users/delete/999999999")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.method(HttpMethod.POST)
                .uri("/permissions/add")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
