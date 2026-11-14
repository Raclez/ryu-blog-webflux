package com.ryu.blog.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公开路径白名单必须「配置即事实」。
 *
 * <p><b>它守的是什么</b>：白名单是安全边界 —— 写进去一条，就是多一个匿名可读的端点。
 * 它原先硬编码在 {@code SaTokenConfig} 的 {@code addExclude(...)} 里，
 * 于是「哪些端点匿名可达」既没法审计、也没法按环境调整。
 * 现在挪到了配置 {@code blog.auth.public-paths}，于是就有了两个方向要守：
 * <ol>
 *   <li><b>配置里的每一条都必须真的被排除</b> —— 否则「配了却没生效」，
 *       公开接口会 401（用户看到的是「明明是公开文章却要登录」）；</li>
 *   <li><b>过滤器里不许出现配置之外的业务路径</b> —— 否则「代码里偷偷加了白名单」，
 *       审计时看配置是干净的，实际却有个匿名可读的端点。</li>
 * </ol>
 *
 * @author ryu
 */
@SpringBootTest
class AuthPublicPathsTest {

    /**
     * 基础设施路径：写死在代码里，不属于「业务公开路径」，不参与第二条断言。
     */
    private static final List<String> INFRA_PREFIXES = List.of(
            "/favicon.ico", "/static", "/doc.html", "/webjars", "/swagger-ui",
            "/v3/api-docs", "/actuator", "/auth/login", "/auth/register",
            "/auth/captcha", "/auth/check");

    @Autowired
    private SaReactorFilter saReactorFilter;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("配置里的公开路径必须真的被排除")
    void configuredPathsAreActuallyExcluded() {
        List<String> configured = configuredPublicPaths();

        assertFalse(configured.isEmpty(), "blog.auth.public-paths 不能为空 —— 否则所有接口都要登录");

        for (String path : configured) {
            assertTrue(saReactorFilter.excludeList.contains(path),
                    "配置里的公开路径必须真的被排除，否则匿名访问会 401: " + path);
        }
    }

    @Test
    @DisplayName("过滤器里不许出现配置之外的业务路径")
    void noSneakyBusinessPathsInExcludeList() {
        List<String> configured = configuredPublicPaths();

        for (String excluded : saReactorFilter.excludeList) {
            if (isInfraPath(excluded)) {
                continue;
            }
            assertTrue(configured.contains(excluded),
                    "排除列表里出现了配置之外的业务路径 —— 「哪些端点匿名可达」看配置是干净的，"
                            + "实际却多了一个匿名可读的端点: " + excluded);
        }
    }

    private List<String> configuredPublicPaths() {
        // YAML 里是一个列表，getProperty(name, List.class) 拿不到 —— 用 Binder 绑。
        return Binder.get(environment)
                .bind("blog.auth.public-paths", Bindable.listOf(String.class))
                .orElse(List.of());
    }

    private boolean isInfraPath(String path) {
        return INFRA_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
