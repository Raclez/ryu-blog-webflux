package com.ryu.blog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ryu.blog.dto.SysConfigDTO;
import com.ryu.blog.dto.SysConfigUpdateDTO;
import com.ryu.blog.entity.SysConfig;
import com.ryu.blog.utils.JsonUtils;
import com.ryu.blog.vo.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 系统配置服务接口
 */
public interface SysConfigService {

    /**
     * 获取配置值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    Mono<String> getConfigValue(String key, String defaultValue);

    /**
     * 获取用户配置值
     *
     * @param userId       用户ID
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    Mono<String> getUserConfigValue(Long userId, String key, String defaultValue);

    /**
     * 获取配置信息
     *
     * @param key 配置键
     * @return 配置对象
     */
    Mono<SysConfig> getConfig(String key);
    
    /**
     * 通过ID获取配置信息
     *
     * @param id 配置ID
     * @return 配置对象
     */
    Mono<SysConfig> getConfigById(Long id);

    /**
     * 获取配置列表
     *
     * @param groupPrefix 分组前缀，可为null
     * @return 配置对象列表
     */
    Flux<SysConfig> getConfigList(String groupPrefix);

    /**
     * 获取用户配置列表
     *
     * @param userId 用户ID
     * @return 配置对象列表
     */
    Flux<SysConfig> getUserConfigList(Long userId);
    
    /**
     * 分页获取系统配置
     *
     * @param configKey 配置键（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 系统配置分页结果
     */
    Mono<PageResult<SysConfig>> getSysConfigPage(String configKey, int page, int size);

    /**
     * 搜索配置
     *
     * @param key 配置键或名称关键字
     * @return 配置对象列表
     */
    Flux<SysConfig> searchConfig(String key);

    /**
     * 添加配置
     *
     * @param configDTO 配置DTO
     * @return 添加后的配置对象
     */
    Mono<SysConfig> addConfig(SysConfigDTO configDTO);

    /**
     * 更新配置
     *
     * @param configDTO 配置DTO
     * @return 更新后的配置对象
     */
    Mono<SysConfig> updateConfig(SysConfigUpdateDTO configDTO);

    /**
     * 删除配置
     *
     * @param id 配置ID
     * @return 被删除的配置对象
     */
    Mono<SysConfig> deleteConfig(Long id);

    /**
     * 批量获取配置
     *
     * @param keys 配置键列表
     * @return 配置键值对
     */
    Mono<Map<String, String>> batchGetConfigValues(Iterable<String> keys);

    /**
     * 批量更新配置
     *
     * @param configs 配置键值对
     * @return 是否更新成功
     */
    Mono<Boolean> batchUpdateConfig(Map<String, String> configs);

    /**
     * 清除所有缓存
     *
     * @return 操作结果
     */
    Mono<Boolean> clearAllCache();
    
    /**
     * 获取配置分组列表
     * 
     * @return 配置分组列表，包含分组名称和层级结构
     */
    Mono<Map<String, Object>> getConfigGroups();

    /**
     * 获取公开配置（白名单内的键）
     *
     * <p>只返回 {@code config_key -> config_value}，不带 remark / extra / userId，
     * 供未登录的调用方使用。哪些键算「公开」由 {@code PublicConfigKeys} 决定。
     *
     * @return 公开配置键值对
     */
    Mono<Map<String, String>> getPublicConfigValues();

    // ==================== 类型化读取 ====================
    //
    // 这几方法刻意用 default 实现、并统一走 getConfigValue —— 后者内部经过
    // self.getConfig，是唯一命中 @Cacheable 的读路径。如果在这里改成直接调
    // getConfig，调用方就会绕开缓存代理，白白多打一次配置表。

    /** 全局日志，接口的 default 方法里没法用 @Slf4j */
    Logger TYPED_READ_LOGGER = LoggerFactory.getLogger(SysConfigService.class);

    /**
     * 读取布尔配置
     *
     * <p>大小写不敏感；键不存在、值为空白或不是合法布尔值时返回默认值。
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 布尔值
     */
    default Mono<Boolean> getBoolean(String key, boolean defaultValue) {
        return getConfigValue(key, "")
                .flatMap(value -> isBlank(value)
                        ? Mono.empty()
                        : Mono.just(Boolean.parseBoolean(value.trim())))
                .defaultIfEmpty(defaultValue);
    }

    /**
     * 读取整数配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 整数值
     */
    default Mono<Integer> getInt(String key, int defaultValue) {
        return getConfigValue(key, "")
                .flatMap(value -> parseNumber(key, value)
                        .map(Number::intValue)
                        .map(Mono::just)
                        .orElseGet(Mono::empty))
                .defaultIfEmpty(defaultValue);
    }

    /**
     * 读取长整数配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 长整数值
     */
    default Mono<Long> getLong(String key, long defaultValue) {
        return getConfigValue(key, "")
                .flatMap(value -> parseNumber(key, value)
                        .map(Number::longValue)
                        .map(Mono::just)
                        .orElseGet(Mono::empty))
                .defaultIfEmpty(defaultValue);
    }

    /**
     * 把 config_value 当 JSON 解析
     *
     * <p>AI Provider 的配置就是这么存的（见 {@code AiProviderConfigValue}）。
     * 解析失败时记警告并返回空 —— 一条坏配置不该让整个调用方挂掉。
     *
     * @param key 配置键
     * @return JSON 树；键不存在或格式非法时为空
     */
    default Mono<JsonNode> getJsonConfig(String key) {
        return getConfigValue(key, "")
                .flatMap(value -> parseJson(key, value));
    }

    /**
     * 把 extra 字段当 JSON 解析
     *
     * <p>注意它读的是实体而不是 config_value，所以走 {@code getConfig(key)}。
     * 而 default 方法里的调用发生在上游代理<b>内部</b>，不会命中 {@code @Cacheable} ——
     * 这里会多打一次配置表。extra 目前的消费方（AI Provider）自己解析 JSON，
     * 这个方法还没有热路径调用者，暂时接受这点开销；哪天有人高频调用，
     * 应该在实现类里补一个带缓存的版本，而不是指望 default 方法。
     *
     * @param key 配置键
     * @return JSON 树；配置不存在、extra 为空或格式非法时为空
     */
    default Mono<JsonNode> getExtraConfig(String key) {
        // 不能写成 .map(SysConfig::getExtra)：extra 为空时 map 会返回 null，
        // Reactor 直接抛 NullPointerException。用 flatMap 把 null 变成空流。
        return getConfig(key)
                .flatMap(config -> parseJson(key + ".extra", config.getExtra()));
    }

    /** 空白判定（null / 空串 / 纯空格都算没配） */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 解析数字，非法值记日志后返回空 */
    private static java.util.Optional<Number> parseNumber(String key, String value) {
        if (isBlank(value)) {
            return java.util.Optional.empty();
        }
        String trimmed = value.trim();
        try {
            return java.util.Optional.of(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            try {
                return java.util.Optional.of(Double.parseDouble(trimmed));
            } catch (NumberFormatException ignored) {
                TYPED_READ_LOGGER.warn("配置值不是数字 - key: {}, value: {}", key, value);
                return java.util.Optional.empty();
            }
        }
    }

    /** 解析 JSON，非法值记日志后返回空 */
    private static Mono<JsonNode> parseJson(String key, String value) {
        if (isBlank(value)) {
            return Mono.empty();
        }
        try {
            return Mono.justOrEmpty(JsonUtils.parseTree(value));
        } catch (Exception e) {
            TYPED_READ_LOGGER.warn("配置值不是合法 JSON - key: {}", key, e);
            return Mono.empty();
        }
    }
}
