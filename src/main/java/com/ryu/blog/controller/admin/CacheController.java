package com.ryu.blog.controller.admin;

import com.ryu.blog.utils.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存管理控制器
 * 提供缓存监控和管理功能
 * 
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CacheManager cacheManager;
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息
     */
    @GetMapping("/stats")
    public Mono<Result<Map<String, Object>>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        for (String cacheName : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null && cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
                @SuppressWarnings("unchecked")
                com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = 
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
                
                com.github.benmanes.caffeine.cache.stats.CacheStats cacheStats = nativeCache.stats();
                
                Map<String, Object> cacheMetrics = new HashMap<>();
                cacheMetrics.put("size", nativeCache.estimatedSize());
                cacheMetrics.put("hitRate", cacheStats.hitRate());
                cacheMetrics.put("missRate", cacheStats.missRate());
                cacheMetrics.put("hitCount", cacheStats.hitCount());
                cacheMetrics.put("missCount", cacheStats.missCount());
                cacheMetrics.put("loadSuccessCount", cacheStats.loadSuccessCount());
                cacheMetrics.put("loadFailureCount", cacheStats.loadFailureCount());
                cacheMetrics.put("totalLoadTime", cacheStats.totalLoadTime());
                cacheMetrics.put("evictionCount", cacheStats.evictionCount());
                cacheMetrics.put("evictionWeight", cacheStats.evictionWeight());
                
                stats.put(cacheName, cacheMetrics);
            } else {
                stats.put(cacheName, Map.of("status", "无法获取统计信息"));
            }
        }
        
        return Mono.just(Result.success(stats));
    }
    
    /**
     * 清除指定名称的缓存
     * 
     * @param cacheName 缓存名称
     * @return 清除结果
     */
    @DeleteMapping("/clear/{cacheName}")
    public Mono<Result<Boolean>> clearCache(@PathVariable String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("已清除缓存: {}", cacheName);
            return Mono.just(Result.success(true));
        }
        return Mono.just(Result.fail("缓存不存在: " + cacheName));
    }
    
    /**
     * 清除所有缓存
     * 
     * @return 清除结果
     */
    @DeleteMapping("/clear-all")
    public Mono<Result<Boolean>> clearAllCaches() {
        for (String cacheName : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("已清除缓存: {}", cacheName);
            }
        }
        return Mono.just(Result.success(true));
    }
    
    /**
     * 获取所有缓存名称
     * 
     * @return 缓存名称列表
     */
    @GetMapping("/names")
    public Mono<Result<Object>> getCacheNames() {
        return Mono.just(Result.success(cacheManager.getCacheNames()));
    }
}