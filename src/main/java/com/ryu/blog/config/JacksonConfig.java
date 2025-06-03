package com.ryu.blog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson配置类
 * @author ryu
 */
@Configuration
public class JacksonConfig {

    /**
     * 配置ObjectMapper，注册Java 8日期时间模块
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        // 注册Java 8日期时间模块
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }
} 