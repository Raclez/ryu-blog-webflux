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
        
        // 预热分类数据
        categoryService.getAllCategories()
            .collectList()
            .doOnSuccess(categories -> log.info("分类缓存预热完成，加载 {} 条数据", categories.size()))
            .subscribe();
        
        // 预热标签数据
        tagService.getAllTags(true)
            .collectList()
            .doOnSuccess(tags -> log.info("标签缓存预热完成，加载 {} 条数据", tags.size()))
            .subscribe();
        
        // 预热系统配置数据
        sysConfigService.getSysConfigPage(null, 1, 100)
            .doOnSuccess(config -> log.info("系统配置缓存预热完成，加载页面数据"))
            .subscribe();
        
        log.info("系统缓存预热任务已提交");
    }
} 