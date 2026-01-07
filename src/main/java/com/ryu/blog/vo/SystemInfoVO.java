package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 系统信息VO
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统信息")
public class SystemInfoVO {

    @Schema(description = "操作系统名称")
    private String osName;

    @Schema(description = "操作系统版本")
    private String osVersion;

    @Schema(description = "操作系统架构")
    private String osArch;

    @Schema(description = "可用处理器数量")
    private Integer availableProcessors;

    @Schema(description = "CPU使用率(%)")
    private Double cpuUsage;

    @Schema(description = "系统负载(1分钟)")
    private Double systemLoadAverage;

    @Schema(description = "JVM总内存(MB)")
    private Long totalMemory;

    @Schema(description = "JVM可用内存(MB)")
    private Long freeMemory;

    @Schema(description = "JVM已用内存(MB)")
    private Long usedMemory;

    @Schema(description = "JVM最大内存(MB)")
    private Long maxMemory;

    @Schema(description = "内存使用率(%)")
    private Double memoryUsage;

    @Schema(description = "堆内存详情")
    private HeapMemoryDetail heapMemory;

    @Schema(description = "非堆内存详情")
    private NonHeapMemoryDetail nonHeapMemory;

    @Schema(description = "磁盘信息列表")
    private List<DiskInfo> disks;

    @Schema(description = "JVM启动时间")
    private String startTime;

    @Schema(description = "JVM运行时间(毫秒)")
    private Long uptime;

    @Schema(description = "JVM名称")
    private String jvmName;

    @Schema(description = "JVM版本")
    private String jvmVersion;

    @Schema(description = "系统健康状态")
    private HealthStatus healthStatus;

    @Schema(description = "GC信息")
    private GCInfo gcInfo;

    @Schema(description = "JVM类加载信息")
    private ClassLoadingInfo classLoadingInfo;

    @Schema(description = "缓存统计信息")
    private CacheStats cacheStats;

    @Schema(description = "数据库连接池信息")
    private DataSourceStats dataSourceStats;

    /**
     * 系统健康状态枚举
     */
    public enum HealthStatus {
        /** 健康状态 */
        HEALTHY,
        /** 警告状态 */
        WARNING,
        /** 危险状态 */
        DANGER
    }

    /**
     * GC信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "GC信息")
    public static class GCInfo {
        @Schema(description = "Young GC次数")
        private Long youngGcCount;

        @Schema(description = "Young GC时间(ms)")
        private Long youngGcTime;

        @Schema(description = "Full GC次数")
        private Long fullGcCount;

        @Schema(description = "Full GC时间(ms)")
        private Long fullGcTime;

        @Schema(description = "总GC次数")
        private Long totalGcCount;

        @Schema(description = "总GC时间(ms)")
        private Long totalGcTime;
    }

    /**
     * 堆内存详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "堆内存详情")
    public static class HeapMemoryDetail {
        @Schema(description = "已初始化内存(MB)")
        private Long init;

        @Schema(description = "已使用内存(MB)")
        private Long used;

        @Schema(description = "已提交内存(MB)")
        private Long committed;

        @Schema(description = "最大内存(MB)")
        private Long max;

        @Schema(description = "使用率(%)")
        private Double usagePercent;
    }

    /**
     * 非堆内存详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "非堆内存详情")
    public static class NonHeapMemoryDetail {
        @Schema(description = "已初始化内存(MB)")
        private Long init;

        @Schema(description = "已使用内存(MB)")
        private Long used;

        @Schema(description = "已提交内存(MB)")
        private Long committed;

        @Schema(description = "最大内存(MB)")
        private Long max;

        @Schema(description = "使用率(%)")
        private Double usagePercent;
    }

    /**
     * 磁盘信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "磁盘信息")
    public static class DiskInfo {
        @Schema(description = "挂载点")
        private String mountPoint;

        @Schema(description = "文件系统类型")
        private String fileSystem;

        @Schema(description = "总空间(GB)")
        private Long totalSpace;

        @Schema(description = "可用空间(GB)")
        private Long freeSpace;

        @Schema(description = "已用空间(GB)")
        private Long usedSpace;

        @Schema(description = "使用率(%)")
        private Double usagePercent;
    }

    /**
     * JVM类加载信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "JVM类加载信息")
    public static class ClassLoadingInfo {
        @Schema(description = "当前已加载类数量")
        private Integer loadedClassCount;

        @Schema(description = "累计已加载类数量")
        private Long totalLoadedClassCount;

        @Schema(description = "累计已卸载类数量")
        private Long unloadedClassCount;
    }

    /**
     * 缓存统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "缓存统计信息")
    public static class CacheStats {
        @Schema(description = "Redis缓存统计")
        private RedisCacheStats redis;

        @Schema(description = "Caffeine缓存统计")
        private CaffeineCacheStats caffeine;
    }

    /**
     * Redis缓存统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Redis缓存统计")
    public static class RedisCacheStats {
        @Schema(description = "连接状态")
        private String status;

        @Schema(description = "已使用内存(MB)")
        private Double usedMemory;

        @Schema(description = "内存峰值(MB)")
        private Double usedMemoryPeak;

        @Schema(description = "Key总数")
        private Long keyCount;

        @Schema(description = "命中次数")
        private Long hits;

        @Schema(description = "未命中次数")
        private Long misses;

        @Schema(description = "命中率(%)")
        private Double hitRate;

        @Schema(description = "已连接客户端数")
        private Integer connectedClients;
    }

    /**
     * Caffeine缓存统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Caffeine缓存统计")
    public static class CaffeineCacheStats {
        @Schema(description = "缓存总数")
        private Integer cacheCount;

        @Schema(description = "总命中次数")
        private Long totalHits;

        @Schema(description = "总未命中次数")
        private Long totalMisses;

        @Schema(description = "总命中率(%)")
        private Double totalHitRate;

        @Schema(description = "总缓存条目数")
        private Long totalSize;

        @Schema(description = "总驱逐次数")
        private Long totalEvictions;
    }

    /**
     * 数据库连接池信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "数据库连接池信息")
    public static class DataSourceStats {
        @Schema(description = "活跃连接数")
        private Integer activeConnections;

        @Schema(description = "空闲连接数")
        private Integer idleConnections;

        @Schema(description = "总连接数")
        private Integer totalConnections;

        @Schema(description = "最大连接数")
        private Integer maxConnections;

        @Schema(description = "最小空闲连接数")
        private Integer minIdle;

        @Schema(description = "等待连接的线程数")
        private Integer pendingThreads;
    }
}
