package com.ryu.blog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 白名单的<b>路径匹配</b>必须是对的。
 *
 * <p><b>它守的是什么</b>：白名单用 {@code SaPathPatternParserUtil.match(pattern, path)} 匹配，
 * 而它的两个参数<b>顺序是有讲究的</b>：第一个被当成 {@code PathPattern} 解析，
 * 第二个才是被匹配的路径。写反了不会报错，只是<b>匹配结果全错</b> ——
 * 而「匹配结果全错」在这个方向上是 fail-closed（都当成要登录），
 * 另一个方向（把业务路径当成公开）就是安全漏洞。
 *
 * <p>所以这里把两种方向都钉死：公开路径必须真的公开，非公开路径必须真的不公开。
 *
 * @author ryu
 */
class SaTokenAuthFilterPublicPathTest {

    private SaTokenAuthFilter filter() {
        AuthProperties properties = new AuthProperties();
        properties.setPublicPaths(List.of(
                "/posts/front",
                "/posts/detail/**",
                "/comment/article/**",
                "/sysConfig/public"));
        return new SaTokenAuthFilter(properties, null);
    }

    @Test
    @DisplayName("配置里的公开路径（含 ** 通配）必须真的匿名可达")
    void configuredPathsArePublic() {
        SaTokenAuthFilter filter = filter();

        assertTrue(filter.isPublic(exchange("/posts/front")), "/posts/front 必须匿名可达");
        assertTrue(filter.isPublic(exchange("/posts/detail/123")), "/posts/detail/** 必须匹配到具体的文章 id");
        assertTrue(filter.isPublic(exchange("/posts/detail/123/comments")), "多级路径也要匹配");
        assertTrue(filter.isPublic(exchange("/comment/article/88")), "/comment/article/** 必须匹配");
        assertTrue(filter.isPublic(exchange("/sysConfig/public")), "/sysConfig/public 必须匿名可达");
    }

    @Test
    @DisplayName("基础设施路径必须匿名可达")
    void infraPathsArePublic() {
        SaTokenAuthFilter filter = filter();

        assertTrue(filter.isPublic(exchange("/auth/login")));
        assertTrue(filter.isPublic(exchange("/auth/register")));
        assertTrue(filter.isPublic(exchange("/auth/captcha/abc123")), "/auth/captcha/** 必须匹配");
        assertTrue(filter.isPublic(exchange("/static/css/app.css")));
        assertTrue(filter.isPublic(exchange("/doc.html")));
        assertTrue(filter.isPublic(exchange("/actuator/health")));
        assertTrue(filter.isPublic(exchange("/favicon.ico")));
    }

    @Test
    @DisplayName("业务路径绝不能被当成公开路径")
    void businessPathsAreNotPublic() {
        SaTokenAuthFilter filter = filter();

        assertFalse(filter.isPublic(exchange("/users/page")), "/users/page 必须要求登录");
        assertFalse(filter.isPublic(exchange("/users/save")));
        assertFalse(filter.isPublic(exchange("/roles/page")));
        assertFalse(filter.isPublic(exchange("/permissions/list")));
        assertFalse(filter.isPublic(exchange("/menus/tree")));
        assertFalse(filter.isPublic(exchange("/posts/admin/page")), "前缀相近的业务路径不能被通配符误伤");
        assertFalse(filter.isPublic(exchange("/sysConfig/list")), "/sysConfig/public 不能把整个 /sysConfig/** 放开");
        assertFalse(filter.isPublic(exchange("/comment/delete/5")));
        assertFalse(filter.isPublic(exchange("/actuator/env")), "只放行了 health，不是整个 actuator");
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}
