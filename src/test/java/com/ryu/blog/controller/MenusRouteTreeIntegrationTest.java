package com.ryu.blog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ryu.blog.utils.JsonUtils;
import com.ryu.blog.utils.SaTokenUtils;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端驱动菜单：/menus/my 的返回体必须能直接建路由
 *
 * <p>前端的 {@code generateRoutesByBackend} 拿这个树当路由表用，
 * 因此每个节点都必须带 {@code routeName}（vue-router 的 name）——
 * 缺一个，对应的路由就建不出来，页面前端直接不可达。
 *
 * <p>这里用真实的 ADMIN 账号（user_id = 1）验证，因为这个角色的菜单绑定最全；
 * 只读，不写库。
 *
 * @author ryu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class MenusRouteTreeIntegrationTest {

    /** 开发库里的 ADMIN 账号 */
    private static final Long ADMIN_USER_ID = 1L;

    @Autowired
    private WebTestClient webTestClient;

    private String authToken;

    @BeforeEach
    void setUp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());
        authToken = "Bearer " + SaTokenUtils.login(exchange, ADMIN_USER_ID).block();
    }

    @Test
    @DisplayName("ADMIN 的菜单树每个节点都有 routeName，且覆盖关键页面")
    void menuTreeIsRoutingReady() {
        byte[] body = webTestClient.get()
                .uri("/menus/my")
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertNotNull(body);
        JsonNode root = JsonUtils.parseTree(new String(body, StandardCharsets.UTF_8));
        JsonNode data = root.get("data");
        assertTrue(data != null && data.isArray() && !data.isEmpty(),
                "菜单不能为空 —— 为空说明角色绑定没建立，后端模式下侧边栏会全空");

        List<JsonNode> flat = new ArrayList<>();
        flatten(data, flat);
        assertTrue(flat.size() >= 25, "菜单节点过少，疑似种子未生效: " + flat.size());

        for (JsonNode node : flat) {
            JsonNode routeName = node.get("routeName");
            assertTrue(routeName != null && !routeName.asText().isBlank(),
                    "菜单缺少 routeName，前端建不出路由: " + node.get("name"));
        }

        List<String> routeNames = flat.stream()
                .map(node -> node.get("routeName").asText())
                .toList();
        // 覆盖三个层面：一级目录、叶子页面、隐藏路由
        assertTrue(routeNames.contains("SystemManagement"), "缺少一级目录 SystemManagement");
        assertTrue(routeNames.contains("UserList"), "缺少页面 UserList");
        assertTrue(routeNames.contains("Dashboard"), "缺少首页 Dashboard");
        assertTrue(routeNames.contains("ArticleEditor"), "隐藏路由 ArticleEditor 也必须在树里");

        // 路由名重复会让 vue-router 覆盖同名路由
        assertFalse(routeNames.size() != routeNames.stream().distinct().count(),
                "routeName 存在重复: " + routeNames);
    }

    private void flatten(JsonNode nodes, List<JsonNode> out) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            out.add(node);
            flatten(node.get("children"), out);
        }
    }
}
