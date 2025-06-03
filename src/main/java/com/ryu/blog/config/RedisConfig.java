package com.ryu.blog.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 * @author ryu
 */
@Configuration
public class RedisConfig {

    private final ObjectMapper objectMapper;

    public RedisConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        // 配置ObjectMapper以正确处理类型信息，包括数组
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        redisObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.WRAPPER_ARRAY);  // 使用WRAPPER_ARRAY而不是PROPERTY

        // 创建更加通用的Jackson序列化器，使用构造方法传入ObjectMapper
        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper, Object.class);

        RedisSerializationContext<String, Object> context = RedisSerializationContext
                .<String, Object>newSerializationContext()
                .key(keySerializer)
                .value(valueSerializer)
                .hashKey(keySerializer)
                .hashValue(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        StringRedisSerializer valueSerializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(keySerializer)
                .value(valueSerializer)
                .hashKey(keySerializer)
                .hashValue(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

} 