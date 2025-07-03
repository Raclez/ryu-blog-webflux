package com.ryu.blog.config;

import com.ryu.blog.service.CategoryService;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

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
    
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        log.info("开始预热系统缓存...");
        
        // 预热分类基本数据
        log.info("正在预热分类基本数据...");
        categoryService.getAllCategories()
            .collectList()
            .doOnSuccess(categories -> log.info("分类基本数据缓存预热完成，加载 {} 条数据", categories.size()))
            .doOnError(e -> log.error("分类基本数据缓存预热失败: {}", e.getMessage()))
            .subscribe();
        
        // 预热分类统计数据
        log.info("正在预热分类统计数据...");
        categoryService.getAllCategoriesWithArticleCount()
            .collectList()
            .doOnSuccess(stats -> log.info("分类统计数据缓存预热完成，加载 {} 条数据", stats.size()))
            .doOnError(e -> log.error("分类统计数据缓存预热失败: {}", e.getMessage()))
            .subscribe();
        
        // 注意：不要预热分页缓存，应该在实际需要时按需加载
        
        // 预热标签数据
        log.info("正在预热标签数据...");
        tagService.getAllTags(true)
            .collectList()
            .doOnSuccess(tags -> log.info("标签缓存预热完成，加载 {} 条数据", tags.size()))
            .doOnError(e -> log.error("标签缓存预热失败: {}", e.getMessage()))
            .subscribe();
        
        // 预热系统配置数据
        log.info("正在预热系统配置数据...");
        sysConfigService.getSysConfigPage(null, 1, 100)
            .doOnSuccess(config -> log.info("系统配置缓存预热完成，加载页面数据"))
            .doOnError(e -> log.error("系统配置缓存预热失败: {}", e.getMessage()))
            .subscribe();
        
        log.info("系统缓存预热任务已提交，将在后台完成");
    }
} 