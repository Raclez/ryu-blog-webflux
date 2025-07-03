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
     * 每天凌晨2点同步Redis中的文章浏览量到数据库
     * 防止Redis数据丢失导致浏览量统计不准确
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void syncViewCountsToDatabase() {
        log.info("开始执行定时任务：同步Redis中的文章浏览量到数据库");
        viewHistoryService.syncViewCountsToDatabase()
                .subscribe(
                        count -> log.info("文章浏览量同步完成，共同步 {} 篇文章", count),
                        error -> log.error("文章浏览量同步失败: {}", error.getMessage())
                );
    }
} 