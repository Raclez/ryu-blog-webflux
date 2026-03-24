package com.ryu.blog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class TaskExecutorConfig {

    @Bean(name = "asyncTaskScheduler", destroyMethod = "dispose")
    public Scheduler asyncTaskScheduler() {
        log.info("Initializing virtual thread async task scheduler");


        Scheduler scheduler = Schedulers.fromExecutorService(
                Executors.newVirtualThreadPerTaskExecutor()
        );

        log.info("Virtual thread async task scheduler initialized");

        return scheduler;
    }
}
