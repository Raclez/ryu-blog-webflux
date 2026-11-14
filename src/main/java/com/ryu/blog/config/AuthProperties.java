package com.ryu.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 鉴权相关配置（{@code blog.auth.*}）。
 *
 * <p><b>为什么用 ConfigurationProperties 而不是 {@code @Value}</b>：
 * {@code @Value("${blog.auth.public-paths:}") List<String>} 绑不了 YAML 里的列表 ——
 * 占位符解析出来是 {@code List<Object>}，转换器不认，实测绑进去是空的。
 * 列表型配置一律走 {@code @ConfigurationProperties}。
 *
 * @author ryu
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "blog.auth")
public class AuthProperties {

    /**
     * 匿名可访问的公开路径（白名单）。
     *
     * <p><b>这是安全边界</b> —— 加一条就是多一个匿名可读的端点。
     * 「哪些端点匿名可达」必须能被审计、能按环境调整，所以它在配置里而不是代码里。
     *
     * <p>回归护栏：{@code AuthPublicPathsTest} —— 配置里的每一条都必须真的被排除，
     * 且过滤器里不许出现配置之外的业务路径（防止「代码里偷偷加了白名单」）。
     */
    private List<String> publicPaths = new ArrayList<>();
}
