package com.ryu.blog.vo;

import com.ryu.blog.entity.StorageConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 存储策略视图对象
 *
 * <p><b>存在的唯一理由：脱敏。</b>
 *
 * <p>{@link StorageConfig} 实体上的 {@code config} 列存的是 JSON，
 * 里面是对象存储的 <b>accessKey / secretKey</b> 这类凭据。
 * 实体上 {@code getConfigMap()} 带了 {@code @JsonProperty("config")}，
 * 于是<b>直接把实体返回给前端就等于把 AK/SK 明文回显</b> ——
 * 任何拿到 {@code system:storage:view} 的人都能读到凭据。
 *
 * <p>所以读路径一律经过本 VO：敏感字段被替换成 {@link #MASK}，
 * 非敏感字段（endpoint、bucket、prefix、accessUrl 等）原样保留，
 * 否则运维连「配的是哪个桶」都看不到。
 *
 * @author ryu
 * @since 1.0.0
 */
@Data
@Builder
public class StorageConfigVO {

    /**
     * 脱敏后的固定值
     */
    public static final String MASK = "******";

    /**
     * 敏感键名 —— <b>子串匹配</b>（大小写不敏感，命中即整值替换）
     *
     * <p>用「包含匹配」而不是精确匹配：不同后端叫法不一样
     * （{@code accessKey} / {@code access_key} / {@code akId} /
     * {@code secretKey} / {@code secret} / {@code password}），
     * 精确匹配漏掉一个就等于没脱敏。
     */
    private static final List<String> SENSITIVE_KEY_PATTERNS = List.of(
            "accesskey",
            "access_key",
            "secretkey",
            "secret_key",
            "secret",
            "password",
            "passwd",
            "token",
            "credential",
            "privatekey",
            "private_key"
    );

    /**
     * 敏感键名 —— <b>整名匹配</b>
     *
     * <p>{@code ak} / {@code sk} 是 MinIO 凭据的简写，但它同时是
     * {@code task} / {@code disk} / {@code risk} 的<b>子串</b> ——
     * 用子串匹配会把这些完全无害的配置项也一起盖掉，
     * 运维看到的就全是掩码，反而查不出问题。
     * 所以这两个只认整名。
     */
    private static final List<String> SENSITIVE_EXACT_KEYS = List.of(
            "ak",
            "sk"
    );

    private Long id;
    private String strategyName;
    private String strategyKey;
    private Boolean isEnable;
    private Long maxFileSize;
    private Long defaultExpiry;
    private Long creatorId;
    private String accessUrl;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 脱敏后的配置项
     */
    private Map<String, String> config;

    /**
     * 从实体构造脱敏视图
     *
     * @param config 存储配置实体（可以为空）
     * @return 脱敏后的视图
     */
    public static StorageConfigVO from(StorageConfig config) {
        if (config == null) {
            return null;
        }
        return StorageConfigVO.builder()
                .id(config.getId())
                .strategyName(config.getStrategyName())
                .strategyKey(config.getStrategyKey())
                .isEnable(config.getIsEnable())
                .maxFileSize(config.getMaxFileSize())
                .defaultExpiry(config.getDefaultExpiry())
                .creatorId(config.getCreatorId())
                .accessUrl(config.getAccessUrl())
                .description(config.getDescription())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .config(maskSensitive(config.getConfigMap()))
                .build();
    }

    /**
     * 把配置项里的敏感值替换成掩码
     *
     * @param configMap 原始配置项
     * @return 脱敏后的配置项
     */
    public static Map<String, String> maskSensitive(Map<String, String> configMap) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (configMap == null) {
            return masked;
        }
        configMap.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if (isSensitive(key)) {
                // 空值保持空 —— 「没配」和「配了但看不见」是两件事
                masked.put(key, (value == null || value.isEmpty()) ? "" : MASK);
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }

    /**
     * 把「回传的掩码值」还原成库里存着的真实凭据。
     *
     * <p>读接口 {@link #from(StorageConfig)} 把 AK/SK 替换成 {@link #MASK} 返回，
     * 前端编辑表单却是整份 {@code config} 回传的 —— 不做处理就会拿一串星号
     * 覆盖掉库里的真实凭据，下一次上传直接 {@code InvalidAccessKeyId}。
     *
     * <p>规则：<b>敏感键</b>的值等于掩码时用 {@code stored} 里的真值顶上；
     * 库里本来也没有这个键，就<b>不写</b>（保持「没配」），绝不把星号落库。
     * 非敏感键（endpoint / bucket / prefix 等）原样保留，页面改了就要生效。
     *
     * @param incoming 前端回传的配置项（可能是脱敏后的）
     * @param stored   库里当前的配置项（明文）
     * @return 可以安全落库的配置项
     */
    public static Map<String, String> mergeMasked(Map<String, String> incoming, Map<String, String> stored) {
        if (incoming == null) {
            return stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
        }
        Map<String, String> merged = new LinkedHashMap<>();
        incoming.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if (MASK.equals(value) && isSensitive(key)) {
                String real = stored != null ? stored.get(key) : null;
                // 库里也没有真值 —— 与其存一串星号，不如不存
                if (real != null && !real.isEmpty() && !MASK.equals(real)) {
                    merged.put(key, real);
                }
                return;
            }
            merged.put(key, value);
        });
        return merged;
    }

    /**
     * 判断某个配置键是不是敏感项
     *
     * @param key 配置键
     * @return 敏感返回 true
     */
    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
        if (SENSITIVE_EXACT_KEYS.contains(normalized)) {
            return true;
        }
        return SENSITIVE_KEY_PATTERNS.stream().anyMatch(normalized::contains);
    }
}
