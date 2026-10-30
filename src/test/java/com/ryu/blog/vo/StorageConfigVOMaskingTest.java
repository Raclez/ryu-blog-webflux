package com.ryu.blog.vo;

import com.ryu.blog.entity.StorageConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储配置脱敏规则的单元测试
 *
 * <p><b>这条规则是一道凭据防线</b>：{@code t_storage_config.config} 里存的是
 * 对象存储的 accessKey / secretKey。实体 {@link StorageConfig} 上的
 * {@code getConfigMap()} 带着 {@code @JsonProperty("config")}，
 * 一旦有人直接把实体返回给前端，AK/SK 就是<b>明文回显</b> ——
 * 拿到 {@code system:storage:view} 的人能直接接管对象存储桶。
 *
 * <p>读路径一律走 {@link StorageConfigVO}，敏感值被替换成掩码。
 *
 * @author ryu
 */
class StorageConfigVOMaskingTest {

    @Test
    @DisplayName("accessKey / secretKey 等敏感项被掩码")
    void sensitiveKeysAreMasked() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("accessKey", "AKIAIOSFODNN7EXAMPLE");
        config.put("secretKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        config.put("password", "p@ssw0rd");
        config.put("token", "eyJhbGciOi");

        Map<String, String> masked = StorageConfigVO.maskSensitive(config);

        assertEquals(StorageConfigVO.MASK, masked.get("accessKey"));
        assertEquals(StorageConfigVO.MASK, masked.get("secretKey"));
        assertEquals(StorageConfigVO.MASK, masked.get("password"));
        assertEquals(StorageConfigVO.MASK, masked.get("token"));
    }

    @Test
    @DisplayName("不同后端的命名变体都能被识别")
    void namingVariantsAreRecognized() {
        assertTrue(StorageConfigVO.isSensitive("accessKey"));
        assertTrue(StorageConfigVO.isSensitive("ACCESS_KEY"));
        assertTrue(StorageConfigVO.isSensitive("access-key"));
        assertTrue(StorageConfigVO.isSensitive("secret_key"));
        assertTrue(StorageConfigVO.isSensitive("secretKey"));
        assertTrue(StorageConfigVO.isSensitive("privateKey"));
        assertTrue(StorageConfigVO.isSensitive("ak"));
        assertTrue(StorageConfigVO.isSensitive("sk"));
    }

    @Test
    @DisplayName("非敏感项原样保留 —— 运维还得看得见配的是哪个桶")
    void nonSensitiveKeysArePreserved() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("endpoint", "https://oss-cn-hangzhou.aliyuncs.com");
        config.put("bucket", "ryu-blog");
        config.put("region", "cn-hangzhou");
        config.put("prefix", "images");
        config.put("accessUrl", "https://cdn.example.com");

        Map<String, String> masked = StorageConfigVO.maskSensitive(config);

        assertEquals("https://oss-cn-hangzhou.aliyuncs.com", masked.get("endpoint"));
        assertEquals("ryu-blog", masked.get("bucket"));
        assertEquals("cn-hangzhou", masked.get("region"));
        assertEquals("images", masked.get("prefix"));
        assertEquals("https://cdn.example.com", masked.get("accessUrl"));
    }

    @Test
    @DisplayName("ak / sk 只认整名 —— 不把 task / disk / risk 误伤")
    void shortKeysAreExactMatchOnly() {
        assertFalse(StorageConfigVO.isSensitive("task"));
        assertFalse(StorageConfigVO.isSensitive("disk"));
        assertFalse(StorageConfigVO.isSensitive("risk"));
        assertFalse(StorageConfigVO.isSensitive("mask"));
    }

    @Test
    @DisplayName("没配值的敏感项保持空 —— 「没配」和「配了但看不见」是两件事")
    void emptySensitiveValueStaysEmpty() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("accessKey", "");
        config.put("secretKey", null);

        Map<String, String> masked = StorageConfigVO.maskSensitive(config);

        assertEquals("", masked.get("accessKey"));
        assertEquals("", masked.get("secretKey"));
    }

    @Test
    @DisplayName("from(entity) 的输出里没有明文凭据")
    void voBuiltFromEntityHasNoPlainSecret() {
        StorageConfig entity = new StorageConfig();
        entity.setId(1L);
        entity.setStrategyKey("minio");
        entity.setStrategyName("MinIO");
        Map<String, String> config = new LinkedHashMap<>();
        config.put("accessKey", "AKIAIOSFODNN7EXAMPLE");
        config.put("secretKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        config.put("endpoint", "https://minio.example.com");
        entity.setConfigMap(config);

        StorageConfigVO vo = StorageConfigVO.from(entity);

        assertEquals(StorageConfigVO.MASK, vo.getConfig().get("accessKey"));
        assertEquals(StorageConfigVO.MASK, vo.getConfig().get("secretKey"));
        assertEquals("https://minio.example.com", vo.getConfig().get("endpoint"));
        assertEquals("minio", vo.getStrategyKey());
    }

    @Test
    @DisplayName("null 入参不炸")
    void nullInputsAreSafe() {
        assertEquals(0, StorageConfigVO.maskSensitive(null).size());
        assertFalse(StorageConfigVO.isSensitive(null));
    }

    @Test
    @DisplayName("回传的掩码用库里的真值顶上 —— 编辑表单不能把 AK/SK 覆盖成 ******")
    void mergeMaskedKeepsStoredSecret() {
        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("endpoint", "https://minio.example.com");
        incoming.put("bucket", "ryu-blog-2");
        incoming.put("accessKey", StorageConfigVO.MASK);
        incoming.put("secretKey", StorageConfigVO.MASK);

        Map<String, String> stored = new LinkedHashMap<>();
        stored.put("endpoint", "https://minio.example.com");
        stored.put("bucket", "ryu-blog");
        stored.put("accessKey", "REAL_AK");
        stored.put("secretKey", "REAL_SK");

        Map<String, String> merged = StorageConfigVO.mergeMasked(incoming, stored);

        assertEquals("REAL_AK", merged.get("accessKey"));
        assertEquals("REAL_SK", merged.get("secretKey"));
        // 非敏感项以页面上的为准 —— 改了就得生效
        assertEquals("ryu-blog-2", merged.get("bucket"));
    }

    @Test
    @DisplayName("库里也没有原值时不把星号落库")
    void mergeMaskedDropsMaskWithoutStoredValue() {
        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("accessKey", StorageConfigVO.MASK);

        Map<String, String> merged = StorageConfigVO.mergeMasked(incoming, new LinkedHashMap<>());

        assertFalse(merged.containsKey("accessKey"));
    }

    @Test
    @DisplayName("非敏感键上的同字面值不会被当成掩码")
    void mergeMaskedLeavesLiteralStarsOnNonSensitiveKey() {
        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("remark", StorageConfigVO.MASK);

        Map<String, String> merged = StorageConfigVO.mergeMasked(incoming, null);

        assertEquals(StorageConfigVO.MASK, merged.get("remark"));
    }

    @Test
    @DisplayName("前端没传 config（局部更新）时保留库里的全部配置")
    void mergeMaskedWithoutIncomingKeepsStored() {
        Map<String, String> stored = new LinkedHashMap<>();
        stored.put("accessKey", "REAL_AK");

        Map<String, String> merged = StorageConfigVO.mergeMasked(null, stored);

        assertEquals("REAL_AK", merged.get("accessKey"));
    }
}
