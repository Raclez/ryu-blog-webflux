package com.ryu.blog.config;

import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token配置类
 * @author ryu
 */
@Slf4j
@Configuration
public class SaTokenConfig {

    /**
     * 使用JWT无状态登录
     */
    @Bean
    public StpLogic stpLogic() {
        return new StpLogicJwtForMixin();
    }

    /**
     * 注册Sa-Token全局过滤器
     */
    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")
                // 排除地址
                .addExclude(
                        // 静态资源
                        "/favicon.ico",
                        "/static/**",
                        
                        // API文档资源
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                )
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    // 登录校验 - 排除公开接口
                    SaRouter.match("/**")
                            // 身份认证相关
                            .notMatch("/auth/login", "/auth/register")
                            .notMatch("/auth/captcha", "/auth/captcha/gif", "/auth/captcha/arithmetic")
                            .notMatch("/auth/check/username", "/auth/check/email", "/auth/check")
                            .notMatch("/user/register", "/user/login", "/user/captcha")
                            // 内容相关
                            .notMatch("/content/article/list", "/content/article/detail/**")
                            .notMatch("/content/category/list", "/content/tag/list")
                            // 通用接口
                            .notMatch("/ip/info", "/ip/query")
                            .check(r -> {
                                try {
                                    StpUtil.checkLogin();
                                } catch (Exception e) {
                                    log.debug("登录校验失败: {}", e.getMessage());
                                    throw e;
                                }
                            });
                    
                    // 权限认证 - 后台管理接口
                    SaRouter.match("/admin/**")
                            .check(r -> StpUtil.checkRole("admin"));
                })
                // 异常处理
                .setError(e -> {
                    log.error("Sa-Token鉴权异常: {}", e.getMessage());
                    return com.ryu.blog.utils.Result.error(e.getMessage());
                });
    }
} 