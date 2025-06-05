package com.ryu.blog.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * JSON操作工具类
 * 提供对JSON的序列化和反序列化等常用操作
 * 内部使用Jackson实现，支持Java 8时间类型
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
public final class JsonUtils {

    /**
     * ObjectMapper实例
     * Jackson的核心类，用于JSON操作
     * 使用静态初始化块配置
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // 配置ObjectMapper
        log.debug("初始化JsonUtils的ObjectMapper配置");
        
        // 启用美化输出
        OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);

        // 注册Java8时间模块（关键配置）
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        
        // 禁用时间戳格式（保证输出为字符串）
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 设置时区（根据业务需求调整）
        OBJECT_MAPPER.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    }
    
    /**
     * 私有构造方法，防止实例化
     */
    private JsonUtils() {
        throw new UnsupportedOperationException("工具类不支持实例化");
    }

    /**
     * 将对象序列化为JSON字符串
     *
     * @param object 要序列化的对象
     * @return JSON字符串，如果序列化失败返回null
     */
    public static String serialize(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("将对象序列化为JSON字符串失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将对象序列化为美化格式的JSON字符串
     * 适用于日志打印或调试输出
     *
     * @param object 要序列化的对象
     * @return 美化后的JSON字符串，如果序列化失败返回null
     */
    public static String serializePretty(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("将对象序列化为美化JSON字符串失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将JSON字符串反序列化为Java对象
     * 
     * @param json JSON字符串
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 反序列化后的Java对象，如果反序列化失败返回null
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        if (json == null || json.isEmpty() || clazz == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            log.error("将JSON字符串反序列化为对象失败: {} -> {}", json, clazz.getName(), e);
            return null;
        }
    }
    
    /**
     * 将JSON字符串反序列化为Java对象列表
     * 
     * @param json JSON字符串
     * @param elementClass 列表元素类型
     * @param <T> 泛型类型
     * @return 反序列化后的Java对象列表，如果反序列化失败返回空列表
     */
    public static <T> List<T> deserializeList(String json, Class<T> elementClass) {
        if (json == null || json.isEmpty() || elementClass == null) {
            return Collections.emptyList();
        }
        
        try {
            JavaType type = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass);
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            log.error("将JSON字符串反序列化为对象列表失败: {} -> List<{}>", json, elementClass.getName(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 将JSON字符串反序列化为泛型对象
     * 支持复杂类型，如List<User>、Map<String, List<User>>等
     * 
     * @param json JSON字符串
     * @param typeReference 目标类型引用
     * @param <T> 泛型类型
     * @return 反序列化后的泛型对象，如果反序列化失败返回null
     */
    public static <T> T deserialize(String json, TypeReference<T> typeReference) {
        if (json == null || json.isEmpty() || typeReference == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (IOException e) {
            log.error("将JSON字符串反序列化为泛型对象失败: {} -> {}", json, typeReference.getType(), e);
            return null;
        }
    }

    /**
     * 将JSON字符串解析为JsonNode对象
     * 适用于需要逐层解析的复杂JSON
     * 
     * @param json JSON字符串
     * @return JsonNode对象，如果解析失败返回null
     */
    public static JsonNode parseTree(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (IOException e) {
            log.error("将JSON字符串解析为JsonNode失败: {}", json, e);
            return null;
        }
    }

    /**
     * 将JSON字符串转换为Map
     * 
     * @param json JSON字符串
     * @return Map对象，如果转换失败返回空Map
     */
    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.error("将JSON字符串转换为Map失败: {}", json, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 将JSON字符串转换为List
     * 
     * @param json JSON字符串
     * @return List对象，如果转换失败返回空List
     */
    public static List<Object> toList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Object>>() {});
        } catch (IOException e) {
            log.error("将JSON字符串转换为List失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 检查是否为合法的JSON字符串
     * 
     * @param json 要检查的JSON字符串
     * @return 如果是合法的JSON返回true，否则返回false
     */
    public static boolean isValid(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (IOException e) {
            log.debug("JSON格式验证失败: {}", json);
            return false;
        }
    }
    
    /**
     * 将对象转换为JsonNode
     * 适用于需要在转换前后进行节点操作的场景
     * 
     * @param object 要转换的对象
     * @return JsonNode对象，如果转换失败返回null
     */
    public static JsonNode toJsonNode(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.valueToTree(object);
        } catch (Exception e) {
            log.error("将对象转换为JsonNode失败: {}", object, e);
            return null;
        }
    }
    
    /**
     * 获取ObjectMapper实例
     * 谨慎使用，避免修改全局配置
     * 
     * @return Jackson的ObjectMapper实例
     */
    public static ObjectMapper getMapper() {
        return OBJECT_MAPPER;
    }
}


