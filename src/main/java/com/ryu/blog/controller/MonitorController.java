package com.ryu.blog.controller;

import com.ryu.blog.service.MonitorService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.vo.SystemInfoVO;
import com.ryu.blog.vo.ThreadInfoVO;
import com.ryu.blog.vo.ThreadStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 系统监控控制器
 * <p>
 * 提供系统监控相关的RESTful API接口，包括：
 * <ul>
 *   <li>系统信息查询（内存、JVM、健康状态）</li>
 *   <li>线程信息查询和统计</li>
 *   <li>系统性能指标监控</li>
 * </ul>
 * 
 * <p>所有接口均采用响应式编程模型，返回Mono类型的响应式流
 * 
 * @author ryu
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
@Tag(name = "系统监控", description = "系统监控相关接口")
public class MonitorController {

    private final MonitorService monitorService;

    /**
     * 获取系统信息
     * <p>
     * 获取系统基本信息，包括JVM信息、内存使用情况和系统健康状态。
     * 
     * @return 系统信息，包含JVM、内存和健康状态
     */
    @Operation(summary = "获取系统信息", description = "获取系统基本信息，包含内存使用、JVM信息和健康状态")
    @GetMapping("/info")
    public Mono<Result<SystemInfoVO>> getSystemInfo() {
        log.info("[系统监控] 查询系统信息");
        
        return monitorService.getSystemInfo()
                .doOnSuccess(info -> log.debug("[系统监控] 系统信息查询成功 - JVM运行时长: {}ms, 内存使用率: {}%", 
                        info.getUptime(), info.getMemoryUsage()))
                .map(Result::success)
                .doOnError(e -> log.error("[系统监控] 系统信息查询失败 - 错误: {}", e.getMessage(), e));
    }

    /**
     * 获取所有线程信息
     * <p>
     * 获取系统中所有线程的详细信息，包括线程状态、CPU时间、栈信息等。
     * 
     * @return 所有线程的详细信息列表
     */
    @Operation(summary = "获取所有线程信息", description = "获取所有线程的详细信息列表")
    @GetMapping("/threads")
    public Mono<Result<List<ThreadInfoVO>>> getAllThreads() {
        log.info("[系统监控] 查询所有线程信息");
        
        return monitorService.getAllThreads()
                .collectList()
                .doOnSuccess(threads -> log.info("[系统监控] 线程信息查询成功 - 线程总数: {}", threads.size()))
                .map(Result::success)
                .doOnError(e -> log.error("[系统监控] 线程信息查询失败 - 错误: {}", e.getMessage(), e));
    }

    /**
     * 获取线程统计信息
     * <p>
     * 获取线程的统计信息，包括线程总数、活跃线程数、各状态线程数统计、死锁检测等。
     * 相比获取所有线程信息，此接口返回的数据更轻量，适合频繁调用。
     * 
     * @return 线程状态统计和关键线程指标
     */
    @Operation(summary = "获取线程统计信息", description = "获取线程状态统计和关键线程指标")
    @GetMapping("/thread-stats")
    public Mono<Result<ThreadStatsVO>> getThreadStats() {
        log.info("[系统监控] 查询线程统计信息");
        
        return monitorService.getThreadStats()
                .doOnSuccess(stats -> log.debug("[系统监控] 线程统计查询成功 - 总线程数: {}, 活跃线程数: {}, 死锁线程数: {}", 
                        stats.getTotalThreadCount(), stats.getActiveThreadCount(), 
                        stats.getDeadlockedThreadIds() != null ? stats.getDeadlockedThreadIds().size() : 0))
                .map(Result::success)
                .doOnError(e -> log.error("[系统监控] 线程统计查询失败 - 错误: {}", e.getMessage(), e));
    }
}
