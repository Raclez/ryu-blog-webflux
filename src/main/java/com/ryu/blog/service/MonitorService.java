package com.ryu.blog.service;

import com.ryu.blog.vo.SystemInfoVO;
import com.ryu.blog.vo.ThreadInfoVO;
import com.ryu.blog.vo.ThreadStatsVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 系统监控服务接口
 * 
 * @author ryu
 * @since 1.0
 */
public interface MonitorService {

    /**
     * 获取系统信息
     * 
     * @return 系统基本信息，包含内存使用、JVM信息和健康状态
     */
    Mono<SystemInfoVO> getSystemInfo();

    /**
     * 获取所有线程信息
     * 
     * @return 所有线程的详细信息列表
     */
    Flux<ThreadInfoVO> getAllThreads();

    /**
     * 获取线程统计信息
     * 
     * @return 线程状态统计和关键线程指标
     */
    Mono<ThreadStatsVO> getThreadStats();
}
