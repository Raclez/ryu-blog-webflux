package com.ryu.blog.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 *
 * @author ryu
 */
@Configuration
public class RedisConfig {

    /**
     * 允许反序列化的类型前缀白名单
     *
     * <p>见 {@link #buildRedisObjectMapper(ObjectMapper)} 里关于「为什么要白名单」的说明。
     */
    private static final String[] ALLOWED_SUB_TYPE_PREFIXES = {
            "com.ryu.blog.",
            "java.util.",
            "java.time."
    };

    /**
     * 允许反序列化的<b>标量</b>类型（精确匹配，不放行整个 java.lang 包）
     *
     * <p>为什么需要单独列：default typing 只要目标类型是非 final 的就会带上类名。
     * 缓存里最常见的形态是 {@code Map<String, Object>}，它的 value 声明类型是
     * {@code Object}（非 final）—— 于是 {@code Long}、{@code String} 这些
     * **final 类也会带上类名**（实测序列化成 {@code ["java.lang.Long",42]}）。
     *
     * <p>所以 {@code java.lang.} 整个包不能放行（{@code ProcessBuilder} 之类的
     * 非 final 类也在里面），只能把真正用到的标量类型逐个精确放行。
     */
    private static final Class<?>[] ALLOWED_SCALAR_TYPES = {
            String.class,
            Long.class,
            Integer.class,
            Double.class,
            Float.class,
            Short.class,
            Byte.class,
            Boolean.class,
            Character.class,
            java.math.BigDecimal.class,
            java.math.BigInteger.class
    };

    private final ObjectMapper objectMapper;

    public RedisConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(buildRedisObjectMapper(objectMapper), Object.class);

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

    /**
     * 构造 Redis 值序列化用的 ObjectMapper
     *
     * <p><b>安全要点：多态反序列化必须带白名单</b>。
     *
     * <p>这里的缓存值类型是 {@code Object}，所以必须开启 default typing
     * （{@code activateDefaultTyping}）才能把 {@code HashMap}、{@code User}
     * 这类对象原样读回来。而 default typing 意味着 JSON 里带了一个
     * 「类名」，Jackson 会按这个名字把 JSON 变成那个类的实例 ——
     * <b>如果类名不受限制，攻击者只要能往 Redis 里写一个值，
     * 就能让应用把任意类实例化</b>（Jackson 反序列化 gadget 链的经典入口）。
     *
     * <p>原来的写法是 {@code LaissezFaireSubTypeValidator} ——
     * 它的语义就是「全部允许」，等于把这个入口完全敞开。
     *
     * <p>现在换成 {@link BasicPolymorphicTypeValidator} 并限定类名前缀：
     * 只有本项目自己的类（{@code com.ryu.blog.}）和 JDK 容器
     * （{@code java.util.}）能被还原。白名单覆盖了当前实际缓存的所有类型：
     * <ul>
     *   <li>验证码、计数等 —— {@code String}/{@code Long}，是 final 类，
     *       NON_FINAL 模式下根本不带类型名，不走这条路；</li>
     *   <li>评论缓存 —— {@code Comment[]}，类型名 {@code com.ryu.blog.entity.Comment}；</li>
     *   <li>文件下载令牌 —— {@code HashMap}，类型名 {@code java.util.HashMap}。</li>
     * </ul>
     * 序列化格式（{@code WRAPPER_ARRAY}）保持不变，所以已有的缓存条目
     * 依然能反序列化出来，不需要清库。
     *
     * @param base 应用主 ObjectMapper（带 JavaTimeModule 等模块）
     * @return 用于 Redis 值序列化的 ObjectMapper
     */
    static ObjectMapper buildRedisObjectMapper(ObjectMapper base) {
        ObjectMapper redisObjectMapper = base.copy();
        redisObjectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        redisObjectMapper.activateDefaultTyping(
                buildTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.WRAPPER_ARRAY);

        return redisObjectMapper;
    }

    /**
     * 构造多态类型校验器 —— 只放行白名单里的类
     */
    static PolymorphicTypeValidator buildTypeValidator() {
        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder();
        for (Class<?> scalarType : ALLOWED_SCALAR_TYPES) {
            builder.allowIfSubType(scalarType);
        }
        for (String prefix : ALLOWED_SUB_TYPE_PREFIXES) {
            builder.allowIfSubType(prefix);
        }
        return builder.build();
    }
}
