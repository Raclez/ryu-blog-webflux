//package com.ryu.blog.strategy;
//
//import com.ryu.blog.entity.StorageConfig;
//import com.ryu.blog.repository.StorageConfigRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.context.ApplicationEventPublisher;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//import reactor.test.StepVerifier;
//
//import java.util.HashMap;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//public class StorageConfigManagerTest {
//
//    @Mock
//    private StorageConfigRepository storageConfigRepository;
//
//    @Mock
//    private ApplicationEventPublisher eventPublisher;
//
//    @InjectMocks
//    private StorageConfigManager configManager;
//
//    private StorageConfig localConfig;
//    private StorageConfig aliyunConfig;
//
//    @BeforeEach
//    void setUp() {
//        // 设置本地存储配置
//        localConfig = new StorageConfig();
//        localConfig.setId(1L);
//        localConfig.setStrategyKey("local");
//        localConfig.setStrategyName("本地存储");
//        localConfig.setAccessUrl("http://localhost:8080/files");
//        localConfig.setIsEnable(true);
//        localConfig.setIsDeleted(0);
//
//        Map<String, String> localProps = new HashMap<>();
//        localProps.put("basePath", "/tmp/uploads");
//        localProps.put("prefix", "local");
//        localConfig.setConfigMap(localProps);
//
//        // 设置阿里云存储配置
//        aliyunConfig = new StorageConfig();
//        aliyunConfig.setId(2L);
//        aliyunConfig.setStrategyKey("aliyun");
//        aliyunConfig.setStrategyName("阿里云OSS存储");
//        aliyunConfig.setAccessUrl("https://example.oss-cn-beijing.aliyuncs.com");
//        aliyunConfig.setIsEnable(false);
//        aliyunConfig.setIsDeleted(0);
//
//        Map<String, String> aliyunProps = new HashMap<>();
//        aliyunProps.put("endpoint", "oss-cn-beijing.aliyuncs.com");
//        aliyunProps.put("accessKeyId", "your-access-key-id");
//        aliyunProps.put("accessKeySecret", "your-access-key-secret");
//        aliyunProps.put("bucketName", "your-bucket-name");
//        aliyunProps.put("prefix", "aliyun");
//        aliyunConfig.setConfigMap(aliyunProps);
//
//        // 设置Mock行为
//        when(storageConfigRepository.findOneByIsEnableAndIsDeleted(true, 0))
//                .thenReturn(Mono.just(localConfig));
//
//        when(storageConfigRepository.findByStrategyKeyAndIsDeleted("local", 0))
//                .thenReturn(Mono.just(localConfig));
//
//        when(storageConfigRepository.findByStrategyKeyAndIsDeleted("aliyun", 0))
//                .thenReturn(Mono.just(aliyunConfig));
//
//        when(storageConfigRepository.findAllByIsDeleted(0))
//                .thenReturn(Flux.just(localConfig, aliyunConfig));
//    }
//
//    @Test
//    void testGetStrategyConfig() {
//        // 测试获取本地存储配置
//        StepVerifier.create(configManager.getStrategyConfig("local"))
//                .expectNextMatches(config ->
//                    config.getStrategyKey().equals("local") &&
//                    config.getStrategyName().equals("本地存储") &&
//                    config.getAccessUrl().equals("http://localhost:8080/files"))
//                .verifyComplete();
//
//        // 测试获取阿里云存储配置
//        StepVerifier.create(configManager.getStrategyConfig("aliyun"))
//                .expectNextMatches(config ->
//                    config.getStrategyKey().equals("aliyun") &&
//                    config.getStrategyName().equals("阿里云OSS存储") &&
//                    config.getAccessUrl().equals("https://example.oss-cn-beijing.aliyuncs.com"))
//                .verifyComplete();
//
//        // 测试获取不存在的配置
//        when(storageConfigRepository.findByStrategyKeyAndIsDeleted("nonexistent", 0))
//                .thenReturn(Mono.empty());
//
//        StepVerifier.create(configManager.getStrategyConfig("nonexistent"))
//                .verifyComplete();
//    }
//
//    @Test
//    void testGetConfigProperty() {
//        // 初始化配置缓存
//        configManager.getStrategyConfig("local").block();
//        configManager.getStrategyConfig("aliyun").block();
//
//        // 测试获取本地存储配置属性
//        assertEquals("/tmp/uploads", configManager.getConfigProperty("local", "basePath", "default"));
//        assertEquals("local", configManager.getConfigProperty("local", "prefix", "default"));
//        assertEquals("default", configManager.getConfigProperty("local", "nonexistent", "default"));
//
//        // 测试获取阿里云存储配置属性
//        assertEquals("oss-cn-beijing.aliyuncs.com", configManager.getConfigProperty("aliyun", "endpoint", "default"));
//        assertEquals("your-access-key-id", configManager.getConfigProperty("aliyun", "accessKeyId", "default"));
//        assertEquals("default", configManager.getConfigProperty("aliyun", "nonexistent", "default"));
//    }
//
//    @Test
//    void testGetAccessUrl() {
//        // 初始化配置缓存
//        configManager.getStrategyConfig("local").block();
//        configManager.getStrategyConfig("aliyun").block();
//
//        // 测试获取本地存储访问URL
//        assertEquals("http://localhost:8080/files", configManager.getAccessUrl("local"));
//
//        // 测试获取阿里云存储访问URL
//        assertEquals("https://example.oss-cn-beijing.aliyuncs.com", configManager.getAccessUrl("aliyun"));
//
//        // 测试获取不存在的访问URL
//        when(storageConfigRepository.findByStrategyKeyAndIsDeleted("nonexistent", 0))
//                .thenReturn(Mono.empty());
//        assertEquals("", configManager.getAccessUrl("nonexistent"));
//    }
//
//    @Test
//    void testGetAccessUrlAsync() {
//        // 初始化配置缓存
//        configManager.getStrategyConfig("local").block();
//        configManager.getStrategyConfig("aliyun").block();
//
//        // 测试异步获取本地存储访问URL
//        StepVerifier.create(configManager.getAccessUrlAsync("local"))
//                .expectNext("http://localhost:8080/files")
//                .verifyComplete();
//
//        // 测试异步获取阿里云存储访问URL
//        StepVerifier.create(configManager.getAccessUrlAsync("aliyun"))
//                .expectNext("https://example.oss-cn-beijing.aliyuncs.com")
//                .verifyComplete();
//
//        // 测试异步获取不存在的访问URL
//        when(storageConfigRepository.findByStrategyKeyAndIsDeleted("nonexistent", 0))
//                .thenReturn(Mono.empty());
//
//        StepVerifier.create(configManager.getAccessUrlAsync("nonexistent"))
//                .expectNext("")
//                .verifyComplete();
//    }
//
//    @Test
//    void testUpdateConfigProperty() {
//        // 设置Mock行为
//        when(storageConfigRepository.save(any(StorageConfig.class)))
//                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
//
//        // 测试更新本地存储配置属性
//        StepVerifier.create(configManager.updateConfigProperty("local", "newKey", "newValue"))
//                .verifyComplete();
//
//        verify(storageConfigRepository, times(1)).save(argThat(config ->
//            config.getStrategyKey().equals("local") &&
//            config.getConfigMap().containsKey("newKey") &&
//            config.getConfigMap().get("newKey").equals("newValue")));
//
//        verify(eventPublisher, times(1)).publishEvent(any());
//    }
//
//    @Test
//    void testUpdateConfigProperties() {
//        // 设置Mock行为
//        when(storageConfigRepository.save(any(StorageConfig.class)))
//                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
//
//        // 测试批量更新本地存储配置属性
//        Map<String, String> newProps = new HashMap<>();
//        newProps.put("key1", "value1");
//        newProps.put("key2", "value2");
//
//        StepVerifier.create(configManager.updateConfigProperties("local", newProps))
//                .verifyComplete();
//
//        verify(storageConfigRepository, times(1)).save(argThat(config ->
//            config.getStrategyKey().equals("local") &&
//            config.getConfigMap().containsKey("key1") &&
//            config.getConfigMap().get("key1").equals("value1") &&
//            config.getConfigMap().containsKey("key2") &&
//            config.getConfigMap().get("key2").equals("value2")));
//
//        verify(eventPublisher, times(1)).publishEvent(any());
//    }
//}