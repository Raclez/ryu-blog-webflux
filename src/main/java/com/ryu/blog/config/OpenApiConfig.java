package com.ryu.blog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI配置类
 * @author ryu
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port}")
    private String serverPort;

    @Bean
    public OpenAPI ryuBlogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ryu Blog API文档")
                        .description("基于Spring WebFlux开发的博客系统API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Ryu")
                                .email("contact@example.com")
                                .url("https://github.com/ryu"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("本地开发环境"),
                        new Server().url("https://api.example.com").description("生产环境")
                ));
    }
} 