package com.ryu.blog.controller;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 字典端点的访问控制集成测试
 *
 * <p>字典有两副面孔，权限口径必须分开：
 * <ul>
 *   <li><b>渲染只读</b>（{@code /sysDictItem/type/{code}}）：普通作者要拿它渲染
 *       文章编辑器的「访问权限」「许可协议」下拉，<b>必须</b>对任何登录用户可用；</li>
 *   <li><b>管理端点</b>（分页、增删改、清缓存）：只有 {@code system:config:*} 才能进。</li>
 * </ul>
 *
 * <p>用一个库里不存在、也没有任何角色的合成用户验证「无权限」这一侧 ——
 * 它的权限集合必然为空，不依赖开发库里账号的具体角色配置。
 *
 * <p><b>只读</b>：本测试不写库。
 *
 * @author ryu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class DictAccessIntegrationTest {

    private static final Long NO_PERMISSION_USER_ID = 990102L;

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
    @DisplayName("无权限用户仍可按编码取字典（文章编辑器靠它渲染下拉）")
    void renderEndpointIsOpenToAnyLoggedInUser() {
        webTestClient.get()
                .uri("/sysDictItem/type/article_visibility")
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("无权限用户访问字典管理端点 → 403")
    void adminEndpointsRequirePermission() {
        for (String path : new String[]{"/sysDictItem/page", "/sysDictType/page", "/sysDictType/list"}) {
            int status = webTestClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authToken)
                    .exchange()
                    .returnResult(int.class)
                    .getStatus().value();
            assertEquals(403, status, path + " 无权限用户访问应当是 403");
        }
    }

    @Test
    @DisplayName("匿名访问字典端点 → 401")
    void anonymousIsRejected() {
        for (String path : new String[]{"/sysDictItem/type/article_visibility", "/sysDictItem/page"}) {
            int status = webTestClient.get()
                    .uri(path)
                    .exchange()
                    .returnResult(int.class)
                    .getStatus().value();
            assertEquals(401, status, path + " 匿名访问应当是 401");
        }
    }
}
