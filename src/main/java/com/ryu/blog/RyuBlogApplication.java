package com.ryu.blog;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import reactor.tools.agent.ReactorDebugAgent;

import java.util.Arrays;

/**
 * 单体博客应用程序入口类
 * @author ryu
 */
@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "Ryu Blog API",
        version = "1.0.0",
        description = "基于Spring WebFlux开发的博客系统API",
        contact = @Contact(name = "Ryu", email = "475118582@qq.com"),
        license = @License(name = "MIT")
    )
)
public class RyuBlogApplication {

    /**
     * 生产环境 profile 名 —— 这些 profile 下不挂 ReactorDebugAgent
     */
    private static final String[] PROD_PROFILES = {"prod", "production"};

    public static void main(String[] args) {
        Environment environment = SpringApplication.run(RyuBlogApplication.class, args).getEnvironment();

        // ReactorDebugAgent 会给每一个操作符挂上 hook，
        // 装配/取消订阅时打一行堆栈 —— 排查响应式链路非常好用，
        // 但在生产环境它是**持续的日志洪水**（每个请求几百行），
        // 而且会明显拖慢吞吐。所以只在非生产环境开。
        if (!isProduction(environment)) {
            ReactorDebugAgent.init();
        }
    }

    /**
     * 判断当前是否生产环境
     */
    private static boolean isProduction(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> Arrays.asList(PROD_PROFILES).contains(profile.toLowerCase()));
    }
} 