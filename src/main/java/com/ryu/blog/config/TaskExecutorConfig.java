package com.ryu.blog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 任务执行器配置类
 * 配置响应式调度器用于异步任务执行
 * 
 * @author ryu
 */
@Slf4j
@Configuration
public class TaskExecutorConfig {
    
    /**
     * 配置任务执行调度器（响应式）
     * 使用 Reactor 的 Schedulers.boundedElastic() 创建有界弹性调度器
     * 
     * @return Scheduler
     */
    @Bean(name = "asyncTaskScheduler", destroyMethod = "dispose")
    public Scheduler asyncTaskScheduler() {
        log.info("Initializing reactive async task scheduler");
        
        // 使用 boundedElastic 调度器
        // 特点：
        // 1. 适合执行阻塞 I/O 操作
        // 2. 线程数有上限，防止资源耗尽
        // 3. 线程会被复用
        // 4. 默认最大线程数：10 * CPU核心数
        // 5. 默认队列大小：100000
        Scheduler scheduler = Schedulers.newBoundedElastic(
                10,                    // 最大线程数
                100,                   // 队列容量
                "async-task-exec",     // 线程名称前缀
                60,                    // 线程空闲时间（秒）
                true                   // 是否为守护线程
        );
        
        log.info("Reactive async task scheduler initialized: maxThreads=10, queueCapacity=100");
        
        return scheduler;
    }
}
