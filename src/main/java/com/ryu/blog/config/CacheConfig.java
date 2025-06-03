package com.ryu.blog.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Arrays;

/**
 * 缓存配置类
 * 配置Spring Cache和Caffeine缓存管理器
 * 
 * @author ryu 475118582@qq.com
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 默认缓存管理器
     * 适用于大多数场景，过期时间为5分钟
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
            "storageConfig", 
            "storageProperties", 
            "accessUrl",
            "fileMetadata",
            "fileExists"
        ));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .recordStats());
        cacheManager.setAsyncCacheMode(true);
        return cacheManager;
    }
    
    /**
     * 短期缓存管理器
     * 适用于频繁变化的数据，过期时间为1分钟
     */
    @Bean
    public CacheManager shortTermCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
            "tempUrls", 
            "previewUrls", 
            "downloadUrls"
        ));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(500)
                .recordStats());
        cacheManager.setAsyncCacheMode(true);
        return cacheManager;
    }
    
    /**
     * 长期缓存管理器
     * 适用于不经常变化的数据，过期时间为30分钟
     */
    @Bean
    public CacheManager longTermCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
            "fileChecksum", 
            "thumbnails"
        ));
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(200)
                .recordStats());
        cacheManager.setAsyncCacheMode(true);
        return cacheManager;
    }
} 