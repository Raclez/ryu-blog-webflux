package com.ryu.blog;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.tools.agent.ReactorDebugAgent;

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

    public static void main(String[] args) {
        ReactorDebugAgent.init();
        SpringApplication.run(RyuBlogApplication.class, args);
    }
} 