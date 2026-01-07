package com.ryu.blog.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓存监控器
 * 定期输出缓存统计信息，帮助优化缓存配置
 * 
 * @author ryu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheMonitor {

    private final CacheManager cacheManager;
    
    /**
     * 每小时输出一次缓存统计信息
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void logCacheStats() {
        log.info("==================== 缓存统计信息 ====================");
        
        cacheManager.getCacheNames().forEach(cacheName -> {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache) {
                CaffeineCache caffeineCache = (CaffeineCache) cache;
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                CacheStats stats = nativeCache.stats();
                
                long hitCount = stats.hitCount();
                long missCount = stats.missCount();
                long totalCount = hitCount + missCount;
                double hitRate = totalCount > 0 ? (double) hitCount / totalCount * 100 : 0;
                
                log.info("缓存: {} | 命中率: {}% | 命中: {} | 未命中: {} | 加载: {} | 驱逐: {} | 大小: {}",
                    cacheName,
                    String.format("%.2f", hitRate),
                    hitCount,
                    missCount,
                    stats.loadSuccessCount(),
                    stats.evictionCount(),
                    nativeCache.estimatedSize()
                );
            }
        });
        
        log.info("====================================================");
    }
    
    /**
     * 获取指定缓存的统计信息
     */
    public String getCacheStatsInfo(String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache instanceof CaffeineCache) {
            CaffeineCache caffeineCache = (CaffeineCache) cache;
            Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            CacheStats stats = nativeCache.stats();
            
            long hitCount = stats.hitCount();
            long missCount = stats.missCount();
            long totalCount = hitCount + missCount;
            double hitRate = totalCount > 0 ? (double) hitCount / totalCount * 100 : 0;
            
            return String.format(
                "缓存: %s\n命中率: %.2f%%\n命中次数: %d\n未命中次数: %d\n加载成功: %d\n加载失败: %d\n驱逐次数: %d\n当前大小: %d",
                cacheName,
                hitRate,
                hitCount,
                missCount,
                stats.loadSuccessCount(),
                stats.loadFailureCount(),
                stats.evictionCount(),
                nativeCache.estimatedSize()
            );
        }
        return "缓存不存在或不是Caffeine缓存";
    }
}
