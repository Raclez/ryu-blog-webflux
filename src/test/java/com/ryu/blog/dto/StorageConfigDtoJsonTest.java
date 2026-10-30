package com.ryu.blog.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储配置 DTO 的 JSON 契约。
 *
 * <p><b>它守的是什么</b>：前端开关回传的是布尔 {@code true/false}，
 * 而 DTO 上的 {@code isEnable} 曾经是 {@code Integer} ——
 * Jackson 不会把布尔强转成整型，请求在进 Controller 之前就 400：
 * {@code Cannot deserialize value of type java.lang.Integer from Boolean value (token 'JsonToken.VALUE_TRUE')}。
 * 字段类型改回 {@code Boolean} 之后，布尔与 {@code 1/0} 两种写法都要能读，
 * 免得修好前端又踩回去。
 *
 * @author ryu
 */
class StorageConfigDtoJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("更新请求带布尔 isEnable 能正常反序列化")
    void updateDtoAcceptsBooleanIsEnable() throws Exception {
        String json = "{\"id\":1,\"strategyKey\":\"minio\",\"isEnable\":true,"
                + "\"config\":{\"bucket\":\"ryu-blog\",\"accessKey\":\"******\"}}";

        StorageConfigUpdateDTO dto = objectMapper.readValue(json, StorageConfigUpdateDTO.class);

        assertTrue(dto.getIsEnable());
        assertEquals(1L, dto.getId());
        assertEquals("ryu-blog", dto.getConfig().get("bucket"));
    }

    @Test
    @DisplayName("1/0 的老写法仍然兼容")
    void updateDtoStillAcceptsNumericIsEnable() throws Exception {
        assertTrue(objectMapper.readValue("{\"id\":1,\"isEnable\":1}", StorageConfigUpdateDTO.class).getIsEnable());
        assertFalse(objectMapper.readValue("{\"id\":1,\"isEnable\":0}", StorageConfigUpdateDTO.class).getIsEnable());
    }

    @Test
    @DisplayName("创建请求同样接受布尔 isEnable")
    void createDtoAcceptsBooleanIsEnable() throws Exception {
        String json = "{\"strategyKey\":\"minio\",\"strategyName\":\"MinIO\",\"isEnable\":true,"
                + "\"config\":{\"bucket\":\"ryu-blog\"}}";

        StorageConfigCreateDTO dto = objectMapper.readValue(json, StorageConfigCreateDTO.class);

        assertTrue(dto.getIsEnable());
        assertEquals("minio", dto.getStrategyKey());
    }
}
