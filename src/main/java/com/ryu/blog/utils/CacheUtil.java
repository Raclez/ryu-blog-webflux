package com.ryu.blog.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 缓存工具类
 * 提供统一的缓存操作方法
 * 
 * @author ryu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheUtil {

    private final CacheManager cacheManager;
    
    /**
     * 清除指定缓存的所有数据
     */
    public void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("已清除缓存: {}", cacheName);
        } else {
            log.warn("缓存不存在: {}", cacheName);
        }
    }
    
    /**
     * 清除指定缓存的特定key
     */
    public void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("已清除缓存项: {} - {}", cacheName, key);
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCaches() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        cacheNames.forEach(this::clearCache);
        log.info("已清除所有缓存，共 {} 个", cacheNames.size());
    }
    
    /**
     * 获取缓存值
     */
    public <T> T get(String cacheName, Object key, Class<T> type) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            return cache.get(key, type);
        }
        return null;
    }
    
    /**
     * 设置缓存值
     */
    public void put(String cacheName, Object key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
            log.debug("已设置缓存: {} - {}", cacheName, key);
        }
    }
    
    /**
     * 检查缓存是否存在
     */
    public boolean exists(String cacheName) {
        return cacheManager.getCache(cacheName) != null;
    }
    
    /**
     * 检查缓存key是否存在
     */
    public boolean existsKey(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            return cache.get(key) != null;
        }
        return false;
    }
}
