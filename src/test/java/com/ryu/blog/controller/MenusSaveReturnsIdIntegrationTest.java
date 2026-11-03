package com.ryu.blog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ryu.blog.repository.MenusRepository;
import com.ryu.blog.utils.JsonUtils;
import com.ryu.blog.utils.SaTokenUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新建菜单必须回「菜单 ID」，而不是一句提示文案
 *
 * <p>前端拿到 ID 之后紧接着调 {@code POST /menus/bindRoles?menuId=...} 分配角色。
 * 早先 {@code /menus/save} 回的是 {@code "菜单创建成功"}，前端把它当 menuId 传下去，
 * 服务端直接
 * {@code Type mismatch: Failed to convert value of type 'java.lang.String'
 * to required type 'java.lang.Long'; For input string: "菜单创建成功"}。
 *
 * <p>本测试会真的建一条菜单（路由名带随机后缀），并在结束时删掉自己造的那一条 ——
 * 只删自己创建的 id，不碰任何既有数据。
 *
 * @author ryu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class MenusSaveReturnsIdIntegrationTest {

    /** 开发库里的 ADMIN 账号 */
    private static final Long ADMIN_USER_ID = 1L;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MenusRepository menusRepository;

    private String authToken;

    /** 本次测试造出来的菜单 ID，结束时按它清理 */
    private Long createdMenuId;

    @BeforeEach
    void setUp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());
        authToken = "Bearer " + SaTokenUtils.login(exchange, ADMIN_USER_ID).block();
    }

    @AfterEach
    void tearDown() {
        if (createdMenuId != null) {
            // 只删本测试自己造的那一行
            menusRepository.deleteById(createdMenuId).block();
            assertTrue(menusRepository.findById(createdMenuId).block() == null,
                    "测试造出来的菜单必须清理干净，不能留在开发库里");
            createdMenuId = null;
        }
    }

    @Test
    @DisplayName("POST /menus/save 返回数字 ID，并且能直接拿去绑定角色")
    void saveReturnsNumericIdAndBindRolesAcceptsIt() {
        String routeName = "TestSaveReturnsId_" + UUID.randomUUID().toString().replace("-", "");

        byte[] body = webTestClient.post()
                .uri("/menus/save")
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .bodyValue(Map.of(
                        "name", "接口契约测试菜单",
                        "routeName", routeName,
                        "url", "/test/save-returns-id",
                        "menuType", 1,
                        "parentId", 0,
                        "sort", 9999,
                        "icon", "lucide:settings"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertNotNull(body);
        JsonNode data = JsonUtils.parseTree(new String(body, StandardCharsets.UTF_8)).get("data");
        assertNotNull(data, "响应里必须有 data（新建菜单的 ID）");
        String idText = data.asText();
        assertTrue(idText.matches("\\d+"),
                "data 必须是纯数字的菜单ID，而不是提示文案: " + idText);

        createdMenuId = Long.valueOf(idText);

        // 这正是前端紧接着要发的那条请求：menuId 必须是数字
        webTestClient.post()
                .uri("/menus/bindRoles?menuId=" + idText)
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .bodyValue(List.of())
                .exchange()
                .expectStatus().isOk();
    }
}
