package com.ryu.blog.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ryu.blog.constant.CacheConstants;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
     * 适用于大多数场景，过期时间为30分钟
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        // 使用CaffeineCacheManager替代SimpleCacheManager以支持异步操作
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // 启用异步缓存模式
        cacheManager.setAsyncCacheMode(true);
        
        // 设置默认的缓存配置
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats());
        
        // 注册所有缓存名称
        List<String> cacheNames = new ArrayList<>();
        
        // 已有缓存
        cacheNames.add("storageConfig");
        cacheNames.add("storageProperties");
        cacheNames.add("accessUrl");
        cacheNames.add("fileMetadata");
        cacheNames.add("fileExists");
        
        // 系统配置缓存
        cacheNames.add(CacheConstants.SYS_CONFIG_CACHE_NAME);
        
        // 存储相关缓存
        cacheNames.add(CacheConstants.STORAGE_CONFIG_CACHE_NAME);
        cacheNames.add(CacheConstants.STORAGE_PROPERTIES_CACHE_NAME);
        cacheNames.add(CacheConstants.STORAGE_ACCESS_URL_CACHE_NAME);
        cacheNames.add(CacheConstants.STORAGE_CLIENT_CACHE_NAME);
        cacheNames.add(CacheConstants.MULTIPART_UPLOAD_CACHE_NAME);
        
        // 浏览历史统计缓存
        cacheNames.add(CacheConstants.VIEW_HISTORY_PV_CACHE_NAME);
        cacheNames.add(CacheConstants.VIEW_HISTORY_UV_CACHE_NAME);
        cacheNames.add(CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME);
        
        // 内容相关缓存
        cacheNames.add(CacheConstants.POST_CACHE_NAME);
        cacheNames.add(CacheConstants.CATEGORY_CACHE_NAME);
        cacheNames.add(CacheConstants.TAG_CACHE_NAME);
        cacheNames.add(CacheConstants.COMMENT_CACHE_NAME);
        
        // 用户相关缓存
        cacheNames.add(CacheConstants.USER_CACHE_NAME);
        
        // 文件相关缓存
        cacheNames.add(CacheConstants.FILE_CACHE_NAME);
        
        // 统计相关缓存
        cacheNames.add(CacheConstants.STATS_CACHE_NAME);
        
        // 文章缓存细分
        cacheNames.add(CacheConstants.POST_HOT_CACHE_NAME);
        cacheNames.add(CacheConstants.POST_DETAIL_CACHE_NAME);
        cacheNames.add(CacheConstants.POST_FRONT_CACHE_NAME);
        cacheNames.add(CacheConstants.POST_ADMIN_CACHE_NAME);
        
        cacheManager.setCacheNames(cacheNames);
        
        // 为特定缓存注册自定义配置
        registerCustomCaches(cacheManager);
        
        return cacheManager;
    }
    
    /**
     * 为CaffeineCacheManager注册自定义缓存配置
     */
    private void registerCustomCaches(CaffeineCacheManager cacheManager) {
        // 确保所有注册的自定义缓存都支持异步操作
        
        // 系统配置缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.SYS_CONFIG_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(12, TimeUnit.HOURS)
                .maximumSize(200)
                .recordStats()
                .buildAsync());
        
        // 分类缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.CATEGORY_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(4, TimeUnit.HOURS)  // 分类数据变化较少，可以缓存更长时间
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 标签缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.TAG_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.HOURS)  // 标签数据变化较少，可以缓存更长时间
                .maximumSize(300)
                .recordStats()
                .buildAsync());
        
        // 文章缓存 - 根据不同类型设置不同过期时间
        cacheManager.registerCustomCache(CacheConstants.POST_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)  // 默认30分钟
                .maximumSize(500)
                .recordStats()
                .buildAsync());
        
        // 热门文章缓存 - 较长时间缓存
        cacheManager.registerCustomCache(CacheConstants.POST_HOT_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)  // 热门文章变化较慢，可以缓存更长时间
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 文章详情缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.POST_DETAIL_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)  // 文章详情变化不频繁，可以缓存1小时
                .maximumSize(200)
                .recordStats()
                .buildAsync());
        
        // 前台文章列表缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.POST_FRONT_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)  // 前台列表需要较新数据
                .maximumSize(200)
                .recordStats()
                .buildAsync());
        
        // 后台文章列表缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.POST_ADMIN_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)  // 管理后台需要较新数据
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 评论缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.COMMENT_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(300)
                .recordStats()
                .buildAsync());
        
        // 用户缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.USER_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)  // 用户信息变化不频繁
                .maximumSize(300)
                .recordStats()
                .buildAsync());
        
        // 文件缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.FILE_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)  // 文件信息变化很少
                .maximumSize(500)
                .recordStats()
                .buildAsync());
        
        // 统计缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.STATS_CACHE_NAME, 
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)  // 统计数据需要较新
                .maximumSize(100)
                .recordStats()
                .buildAsync());
    }
    
    /**
     * 构建缓存对象 - 保留此方法以兼容可能的旧代码
     * 注意：此方法不支持异步缓存，仅用于向后兼容
     */
    private CaffeineCache buildCache(String name, long expireTime, TimeUnit timeUnit, int maximumSize) {
        return new CaffeineCache(name, 
            Caffeine.newBuilder()
                .expireAfterWrite(expireTime, timeUnit)
                .maximumSize(maximumSize)
                .recordStats()
                .build());
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