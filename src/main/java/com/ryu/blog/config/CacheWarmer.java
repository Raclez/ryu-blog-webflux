package com.ryu.blog.config;

import com.ryu.blog.service.CategoryService;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 缓存预热器
 * 在应用启动完成后预热常用缓存，提高首次访问性能
 * 
 * @author ryu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmer implements ApplicationListener<ApplicationStartedEvent> {

    private final CategoryService categoryService;
    private final TagService tagService;
    private final SysConfigService sysConfigService;
    
    // 预热超时时间
    private static final Duration WARMUP_TIMEOUT = Duration.ofSeconds(30);
    
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        log.info("开始预热系统缓存...");
        
        long startTime = System.currentTimeMillis();
        
        // 使用 Mono.when 等待所有预热任务完成，并添加超时控制
        Mono.when(
            warmupCategories(),
            warmupCategoryStats(),
            warmupTags(),
            warmupSysConfig()
        )
        .timeout(WARMUP_TIMEOUT)
        .doOnSuccess(v -> {
            long duration = System.currentTimeMillis() - startTime;
            log.info("系统缓存预热完成，耗时: {}ms", duration);
        })
        .doOnError(e -> {
            long duration = System.currentTimeMillis() - startTime;
            log.error("系统缓存预热失败，耗时: {}ms, 错误: {}", duration, e.getMessage());
            log.warn("应用将继续启动，但首次访问可能较慢");
        })
        .subscribe();
    }
    
    /**
     * 预热分类基本数据
     */
    private Mono<Void> warmupCategories() {
        return categoryService.getAllCategories()
            .collectList()
            .doOnSubscribe(s -> log.info("正在预热分类基本数据..."))
            .doOnSuccess(categories -> 
                log.info("✓ 分类基本数据缓存预热完成，加载 {} 条数据", categories.size()))
            .doOnError(e -> 
                log.error("✗ 分类基本数据缓存预热失败: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty())  // 失败时继续其他预热任务
            .then();
    }
    
    /**
     * 预热分类统计数据
     */
    private Mono<Void> warmupCategoryStats() {
        return categoryService.getAllCategoriesWithArticleCount()
            .collectList()
            .doOnSubscribe(s -> log.info("正在预热分类统计数据..."))
            .doOnSuccess(stats -> 
                log.info("✓ 分类统计数据缓存预热完成，加载 {} 条数据", stats.size()))
            .doOnError(e -> 
                log.error("✗ 分类统计数据缓存预热失败: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty())
            .then();
    }
    
    /**
     * 预热标签数据
     */
    private Mono<Void> warmupTags() {
        return tagService.getAllTags(true)
            .collectList()
            .doOnSubscribe(s -> log.info("正在预热标签数据..."))
            .doOnSuccess(tags -> 
                log.info("✓ 标签缓存预热完成，加载 {} 条数据", tags.size()))
            .doOnError(e -> 
                log.error("✗ 标签缓存预热失败: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty())
            .then();
    }
    
    /**
     * 预热系统配置数据
     */
    private Mono<Void> warmupSysConfig() {
        return sysConfigService.getSysConfigPage(null, 1, 100)
            .doOnSubscribe(s -> log.info("正在预热系统配置数据..."))
            .doOnSuccess(config -> 
                log.info("✓ 系统配置缓存预热完成"))
            .doOnError(e -> 
                log.error("✗ 系统配置缓存预热失败: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty())
            .then();
    }
} 