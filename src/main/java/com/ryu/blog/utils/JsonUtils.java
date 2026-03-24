package com.ryu.blog.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * JSON操作工具类
 * 提供对JSON的序列化和反序列化等常用操作
 * 内部使用Jackson实现，支持Java 8时间类型
 * 
 * <p>主要功能：
 * <ul>
 *   <li>对象与JSON字符串的相互转换</li>
 *   <li>支持泛型、集合、Map等复杂类型</li>
 *   <li>提供美化输出和紧凑输出两种模式</li>
 *   <li>JSON格式验证</li>
 *   <li>JsonNode操作</li>
 *   <li>安全的异常处理，返回默认值而非抛出异常</li>
 * </ul>
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
public final class JsonUtils {

    /**
     * 标准ObjectMapper实例（紧凑输出）
     * 用于生产环境的JSON序列化，输出紧凑格式
     */
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper(false);
    
    /**
     * 美化ObjectMapper实例（格式化输出）
     * 用于调试和日志输出，输出易读格式
     */
    private static final ObjectMapper PRETTY_MAPPER = createObjectMapper(true);

    /**
     * 创建并配置ObjectMapper实例
     * 
     * @param prettyPrint 是否启用美化输出
     * @return 配置好的ObjectMapper实例
     */
    private static ObjectMapper createObjectMapper(boolean prettyPrint) {
        ObjectMapper mapper = new ObjectMapper();
        
        // 注册Java8时间模块
        mapper.registerModule(new JavaTimeModule());
        
        // 禁用时间戳格式，使用ISO-8601字符串格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 设置时区
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        
        // 忽略未知属性，避免反序列化时因字段不匹配而失败
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 忽略null值字段（可选，根据需求调整）
        // mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // 美化输出配置
        if (prettyPrint) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        
        return mapper;
    }
    
    /**
     * 私有构造方法，防止实例化
     */
    private JsonUtils() {
        throw new UnsupportedOperationException("工具类不支持实例化");
    }

    // ==================== 序列化方法 ====================
    
    /**
     * 将对象序列化为JSON字符串（紧凑格式）
     *
     * @param object 要序列化的对象
     * @return JSON字符串，如果序列化失败返回null
     */
    public static String toJsonString(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("对象序列化为JSON失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 将对象序列化为JSON字符串（紧凑格式）
     * serialize方法的别名，保持向后兼容
     *
     * @param object 要序列化的对象
     * @return JSON字符串，如果序列化失败返回null
     */
    public static String serialize(Object object) {
        return toJsonString(object);
    }

    /**
     * 将对象序列化为美化格式的JSON字符串
     * 适用于日志打印或调试输出
     *
     * @param object 要序列化的对象
     * @return 美化后的JSON字符串，如果序列化失败返回null
     */
    public static String toPrettyJsonString(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return PRETTY_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("对象序列化为美化JSON失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 将对象序列化为美化格式的JSON字符串
     * serializePretty方法的别名，保持向后兼容
     *
     * @param object 要序列化的对象
     * @return 美化后的JSON字符串，如果序列化失败返回null
     */
    public static String serializePretty(Object object) {
        return toPrettyJsonString(object);
    }
    
    /**
     * 将对象序列化为JSON字节数组
     * 适用于网络传输或文件存储
     *
     * @param object 要序列化的对象
     * @return JSON字节数组，如果序列化失败返回null
     */
    public static byte[] toJsonBytes(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            log.error("对象序列化为JSON字节数组失败: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 反序列化方法 ====================
    
    /**
     * 将JSON字符串反序列化为Java对象
     * 
     * @param json JSON字符串
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 反序列化后的Java对象，如果反序列化失败返回null
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty() || clazz == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            log.error("JSON反序列化为对象失败: {} -> {}", json, clazz.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 将JSON字符串反序列化为Java对象
     * deserialize方法的别名，保持向后兼容
     * 
     * @param json JSON字符串
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 反序列化后的Java对象，如果反序列化失败返回null
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        return parseObject(json, clazz);
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
    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        if (json == null || json.trim().isEmpty() || typeReference == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (IOException e) {
            log.error("JSON反序列化为泛型对象失败: {} -> {}", json, typeReference.getType(), e);
            return null;
        }
    }
    
    /**
     * 将JSON字符串反序列化为泛型对象
     * deserialize方法的别名，保持向后兼容
     * 
     * @param json JSON字符串
     * @param typeReference 目标类型引用
     * @param <T> 泛型类型
     * @return 反序列化后的泛型对象，如果反序列化失败返回null
     */
    public static <T> T deserialize(String json, TypeReference<T> typeReference) {
        return parseObject(json, typeReference);
    }
    
    /**
     * 将JSON字符串反序列化为Java对象列表
     * 
     * @param json JSON字符串
     * @param elementClass 列表元素类型
     * @param <T> 泛型类型
     * @return 反序列化后的Java对象列表，如果反序列化失败返回空列表
     */
    public static <T> List<T> parseList(String json, Class<T> elementClass) {
        if (json == null || json.trim().isEmpty() || elementClass == null) {
            return Collections.emptyList();
        }
        
        try {
            JavaType type = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass);
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            log.error("JSON反序列化为列表失败: {} -> List<{}>", json, elementClass.getSimpleName(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 将JSON字符串反序列化为Java对象列表
     * deserializeList方法的别名，保持向后兼容
     * 
     * @param json JSON字符串
     * @param elementClass 列表元素类型
     * @param <T> 泛型类型
     * @return 反序列化后的Java对象列表，如果反序列化失败返回空列表
     */
    public static <T> List<T> deserializeList(String json, Class<T> elementClass) {
        return parseList(json, elementClass);
    }
    
    /**
     * 将JSON字符串反序列化为Map
     * 
     * @param json JSON字符串
     * @param keyClass Map的key类型
     * @param valueClass Map的value类型
     * @param <K> key的泛型类型
     * @param <V> value的泛型类型
     * @return 反序列化后的Map，如果反序列化失败返回空Map
     */
    public static <K, V> Map<K, V> parseMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (json == null || json.trim().isEmpty() || keyClass == null || valueClass == null) {
            return Collections.emptyMap();
        }
        
        try {
            JavaType type = OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, keyClass, valueClass);
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            log.error("JSON反序列化为Map失败: {} -> Map<{}, {}>", 
                    json, keyClass.getSimpleName(), valueClass.getSimpleName(), e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * 从输入流反序列化为Java对象
     * 适用于文件读取或网络流
     * 
     * @param inputStream 输入流
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 反序列化后的Java对象，如果反序列化失败返回null
     */
    public static <T> T parseObject(InputStream inputStream, Class<T> clazz) {
        if (inputStream == null || clazz == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(inputStream, clazz);
        } catch (IOException e) {
            log.error("从输入流反序列化为对象失败: {}", clazz.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 从字节数组反序列化为Java对象
     * 
     * @param bytes JSON字节数组
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 反序列化后的Java对象，如果反序列化失败返回null
     */
    public static <T> T parseObject(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0 || clazz == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (IOException e) {
            log.error("从字节数组反序列化为对象失败: {}", clazz.getSimpleName(), e);
            return null;
        }
    }

    // ==================== 便捷转换方法 ====================
    
    /**
     * 将JSON字符串转换为Map<String, Object>
     * 
     * @param json JSON字符串
     * @return Map对象，如果转换失败返回空Map
     */
    public static Map<String, Object> toMap(String json) {
        return parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 将JSON字符串转换为List<Object>
     * 
     * @param json JSON字符串
     * @return List对象，如果转换失败返回空List
     */
    public static List<Object> toList(String json) {
        return parseObject(json, new TypeReference<List<Object>>() {});
    }
    
    /**
     * 对象深拷贝
     * 通过JSON序列化和反序列化实现对象的深拷贝
     * 
     * @param object 源对象
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 深拷贝后的对象，如果拷贝失败返回null
     */
    public static <T> T deepCopy(Object object, Class<T> clazz) {
        if (object == null || clazz == null) {
            return null;
        }
        
        try {
            String json = OBJECT_MAPPER.writeValueAsString(object);
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            log.error("对象深拷贝失败: {} -> {}", object.getClass().getSimpleName(), clazz.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 对象类型转换
     * 将一个对象转换为另一个类型，通过JSON中转
     * 适用于DTO转换等场景
     * 
     * @param source 源对象
     * @param targetClass 目标类型
     * @param <T> 泛型类型
     * @return 转换后的对象，如果转换失败返回null
     */
    public static <T> T convertValue(Object source, Class<T> targetClass) {
        if (source == null || targetClass == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.convertValue(source, targetClass);
        } catch (IllegalArgumentException e) {
            log.error("对象类型转换失败: {} -> {}", source.getClass().getSimpleName(), targetClass.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 对象类型转换（泛型版本）
     * 
     * @param source 源对象
     * @param typeReference 目标类型引用
     * @param <T> 泛型类型
     * @return 转换后的对象，如果转换失败返回null
     */
    public static <T> T convertValue(Object source, TypeReference<T> typeReference) {
        if (source == null || typeReference == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.convertValue(source, typeReference);
        } catch (IllegalArgumentException e) {
            log.error("对象类型转换失败: {} -> {}", source.getClass().getSimpleName(), typeReference.getType(), e);
            return null;
        }
    }

    // ==================== JsonNode操作方法 ====================
    
    /**
     * 将JSON字符串解析为JsonNode对象
     * 适用于需要逐层解析的复杂JSON
     * 
     * @param json JSON字符串
     * @return JsonNode对象，如果解析失败返回null
     */
    public static JsonNode parseTree(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (IOException e) {
            log.error("JSON解析为JsonNode失败: {}", json, e);
            return null;
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
        } catch (IllegalArgumentException e) {
            log.error("对象转换为JsonNode失败: {}", object.getClass().getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 从JsonNode转换为Java对象
     * 
     * @param node JsonNode对象
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 转换后的Java对象，如果转换失败返回null
     */
    public static <T> T fromJsonNode(JsonNode node, Class<T> clazz) {
        if (node == null || clazz == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            log.error("JsonNode转换为对象失败: {}", clazz.getSimpleName(), e);
            return null;
        }
    }

    // ==================== 验证和工具方法 ====================
    
    /**
     * 检查是否为合法的JSON字符串
     * 
     * @param json 要检查的JSON字符串
     * @return 如果是合法的JSON返回true，否则返回false
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 检查是否为合法的JSON字符串
     * isValidJson方法的别名，保持向后兼容
     * 
     * @param json 要检查的JSON字符串
     * @return 如果是合法的JSON返回true，否则返回false
     */
    public static boolean isValid(String json) {
        return isValidJson(json);
    }
    
    /**
     * 检查是否为JSON对象（而非数组）
     * 
     * @param json JSON字符串
     * @return 如果是JSON对象返回true，否则返回false
     */
    public static boolean isJsonObject(String json) {
        JsonNode node = parseTree(json);
        return node != null && node.isObject();
    }
    
    /**
     * 检查是否为JSON数组
     * 
     * @param json JSON字符串
     * @return 如果是JSON数组返回true，否则返回false
     */
    public static boolean isJsonArray(String json) {
        JsonNode node = parseTree(json);
        return node != null && node.isArray();
    }
    
    /**
     * 美化JSON字符串
     * 将紧凑的JSON字符串格式化为易读格式
     * 
     * @param json 紧凑的JSON字符串
     * @return 美化后的JSON字符串，如果格式化失败返回原字符串
     */
    public static String prettify(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        
        try {
            Object obj = OBJECT_MAPPER.readValue(json, Object.class);
            return PRETTY_MAPPER.writeValueAsString(obj);
        } catch (IOException e) {
            log.error("JSON美化失败: {}", json, e);
            return json;
        }
    }
    
    /**
     * 压缩JSON字符串
     * 将格式化的JSON字符串压缩为紧凑格式
     * 
     * @param json 格式化的JSON字符串
     * @return 压缩后的JSON字符串，如果压缩失败返回原字符串
     */
    public static String minify(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        
        try {
            Object obj = OBJECT_MAPPER.readValue(json, Object.class);
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (IOException e) {
            log.error("JSON压缩失败: {}", json, e);
            return json;
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
    
    /**
     * 获取美化输出的ObjectMapper实例
     * 
     * @return 美化输出的ObjectMapper实例
     */
    public static ObjectMapper getPrettyMapper() {
        return PRETTY_MAPPER;
    }
}
