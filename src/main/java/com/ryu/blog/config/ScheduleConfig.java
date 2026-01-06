package com.ryu.blog.config;

import com.ryu.blog.service.ViewHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置类
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ScheduleConfig {

    private final ViewHistoryService viewHistoryService;

    /**
     * 每天凌晨2点同步缓存中的文章浏览量到数据库
     * 
     * 双重同步策略：
     * 1. 实时同步：每达到阈值（默认10次浏览）自动同步到数据库
     * 2. 定时同步：每天凌晨兜底同步所有缓存数据，确保数据完整性
     * 
     * 这样既能减少数据库写入压力，又能防止缓存数据丢失
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void syncViewCountsToDatabase() {
        log.info("开始执行定时任务：同步缓存中的文章浏览量到数据库");
        viewHistoryService.syncViewCountsToDatabase()
                .subscribe(
                        count -> log.info("定时同步完成，共同步 {} 篇文章的浏览量", count),
                        error -> log.error("定时同步文章浏览量失败: {}", error.getMessage(), error)
                );
    }
} 