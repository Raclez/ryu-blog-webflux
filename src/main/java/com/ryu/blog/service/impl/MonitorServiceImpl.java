package com.ryu.blog.service.impl;

import com.ryu.blog.service.MonitorService;
import com.ryu.blog.vo.SystemInfoVO;
import com.ryu.blog.vo.ThreadInfoVO;
import com.ryu.blog.vo.ThreadStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.lang.management.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 系统监控服务实现
 * 
 * @author ryu
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private static final long MB = 1024 * 1024;
    private static final long GB = 1024 * 1024 * 1024;
    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    
    private final AtomicReference<CpuUsageCache> cpuUsageCache = new AtomicReference<>();
    
    private final CacheManager cacheManager;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ReactiveRedisConnectionFactory redisConnectionFactory;
    private final DatabaseClient databaseClient;

    private static class CpuUsageCache {
        final long[] ticks;
        final double usage;
        final long timestamp;

        CpuUsageCache(long[] ticks, double usage, long timestamp) {
            this.ticks = ticks;
            this.usage = usage;
            this.timestamp = timestamp;
        }
    }

    @PostConstruct
    public void init() {
        updateCpuUsage();
    }

    @Scheduled(fixedRate = 5000)
    public void updateCpuUsage() {
        try {
            CentralProcessor processor = hardware.getProcessor();
            long[] ticks = processor.getSystemCpuLoadTicks();
            
            CpuUsageCache cache = cpuUsageCache.get();
            if (cache != null) {
                double usage = processor.getSystemCpuLoadBetweenTicks(cache.ticks) * 100;
                cpuUsageCache.set(new CpuUsageCache(ticks, usage, System.currentTimeMillis()));
            } else {
                cpuUsageCache.set(new CpuUsageCache(ticks, 0.0, System.currentTimeMillis()));
            }
        } catch (Exception e) {
            log.warn("更新CPU使用率失败: {}", e.getMessage());
        }
    }

    @Override
    @Cacheable(value = "monitor:system", unless = "#result == null")
    public Mono<SystemInfoVO> getSystemInfo() {
        return Mono.defer(() -> {
            // Gather reactive cache stats
            return getCacheStatsReactive()
                    .map(this::buildSystemInfoWithCache)
                    .onErrorResume(e -> {
                        log.warn("获取缓存统计失败: {}", e.getMessage());
                        return Mono.just(buildSystemInfoWithCache(getDefaultCacheStats()));
                    });
        });
    }


    private SystemInfoVO buildSystemInfoWithCache(SystemInfoVO.CacheStats cacheStats) {
        String osName = os.getFamily() + " " + os.getManufacturer();
        String osVersion = os.getVersionInfo().getVersion();
        String osArch = System.getProperty("os.arch");

        CentralProcessor processor = hardware.getProcessor();
        int availableProcessors = processor.getLogicalProcessorCount();
        double cpuUsage = getCachedCpuUsage();
        double systemLoadAverage = processor.getSystemLoadAverage(1)[0];

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / MB;
        long freeMemory = runtime.freeMemory() / MB;
        long maxMemory = runtime.maxMemory() / MB;
        long usedMemory = totalMemory - freeMemory;
        double memoryUsage = (double) usedMemory / maxMemory * 100;

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        SystemInfoVO.HeapMemoryDetail heapMemory = SystemInfoVO.HeapMemoryDetail.builder()
                .init(heapMemoryUsage.getInit() / MB)
                .used(heapMemoryUsage.getUsed() / MB)
                .committed(heapMemoryUsage.getCommitted() / MB)
                .max(heapMemoryUsage.getMax() / MB)
                .usagePercent(Math.round((double) heapMemoryUsage.getUsed() / heapMemoryUsage.getMax() * 10000.0) / 100.0)
                .build();

        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        SystemInfoVO.NonHeapMemoryDetail nonHeapMemory = SystemInfoVO.NonHeapMemoryDetail.builder()
                .init(nonHeapMemoryUsage.getInit() / MB)
                .used(nonHeapMemoryUsage.getUsed() / MB)
                .committed(nonHeapMemoryUsage.getCommitted() / MB)
                .max(nonHeapMemoryUsage.getMax() > 0 ? nonHeapMemoryUsage.getMax() / MB : -1)
                .usagePercent(nonHeapMemoryUsage.getMax() > 0 
                        ? Math.round((double) nonHeapMemoryUsage.getUsed() / nonHeapMemoryUsage.getMax() * 10000.0) / 100.0 
                        : null)
                .build();

        SystemInfoVO.GCInfo gcInfo = getGCInfo();
        List<SystemInfoVO.DiskInfo> disks = getDiskInfo();
        SystemInfoVO.ClassLoadingInfo classLoadingInfo = getClassLoadingInfo();
        SystemInfoVO.DataSourceStats dataSourceStats = getDataSourceStats();

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startTime = sdf.format(new Date(runtimeMXBean.getStartTime()));
        long uptime = runtimeMXBean.getUptime();
        String jvmName = runtimeMXBean.getVmName();
        String jvmVersion = runtimeMXBean.getVmVersion();

        SystemInfoVO.HealthStatus healthStatus = calculateHealthStatus(memoryUsage, cpuUsage, gcInfo);

        return SystemInfoVO.builder()
                .osName(osName)
                .osVersion(osVersion)
                .osArch(osArch)
                .availableProcessors(availableProcessors)
                .cpuUsage(Math.round(cpuUsage * 100.0) / 100.0)
                .systemLoadAverage(Math.round(systemLoadAverage * 100.0) / 100.0)
                .totalMemory(totalMemory)
                .freeMemory(freeMemory)
                .usedMemory(usedMemory)
                .maxMemory(maxMemory)
                .memoryUsage(Math.round(memoryUsage * 100.0) / 100.0)
                .heapMemory(heapMemory)
                .nonHeapMemory(nonHeapMemory)
                .gcInfo(gcInfo)
                .disks(disks)
                .startTime(startTime)
                .uptime(uptime)
                .jvmName(jvmName)
                .jvmVersion(jvmVersion)
                .healthStatus(healthStatus)
                .classLoadingInfo(classLoadingInfo)
                .cacheStats(cacheStats)
                .dataSourceStats(dataSourceStats)
                .build();
    }

    private Mono<SystemInfoVO.CacheStats> getCacheStatsReactive() {
        Mono<SystemInfoVO.RedisCacheStats> redisMono = getRedisCacheStatsReactive();
        Mono<SystemInfoVO.CaffeineCacheStats> caffeineMono = Mono.fromCallable(this::getCaffeineCacheStats);
        
        return Mono.zip(redisMono, caffeineMono)
                .map(tuple -> SystemInfoVO.CacheStats.builder()
                        .redis(tuple.getT1())
                        .caffeine(tuple.getT2())
                        .build());
    }

    private Mono<SystemInfoVO.RedisCacheStats> getRedisCacheStatsReactive() {
        return Mono.usingWhen(
                Mono.fromSupplier(redisConnectionFactory::getReactiveConnection),
                connection -> {
                    Mono<Properties> statsMono = connection.serverCommands().info("stats");
                    Mono<Properties> memoryMono = connection.serverCommands().info("memory");
                    Mono<Properties> clientsMono = connection.serverCommands().info("clients");
                    Mono<Long> dbSizeMono = connection.serverCommands().dbSize();
                    
                    return Mono.zip(statsMono, memoryMono, clientsMono, dbSizeMono)
                            .map(tuple -> {
                                Properties stats = tuple.getT1();
                                Properties memory = tuple.getT2();
                                Properties clients = tuple.getT3();
                                Long dbSize = tuple.getT4();

                                long hits = parseLong(stats.getProperty("keyspace_hits", "0"));
                                long misses = parseLong(stats.getProperty("keyspace_misses", "0"));
                                double usedMemory = parseLong(memory.getProperty("used_memory", "0")) / (1024.0 * 1024.0);
                                double usedMemoryPeak = parseLong(memory.getProperty("used_memory_peak", "0")) / (1024.0 * 1024.0);
                                int connectedClients = parseInt(clients.getProperty("connected_clients", "0"));
                                double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) * 100 : 0.0;

                                return SystemInfoVO.RedisCacheStats.builder()
                                        .status("connected")
                                        .usedMemory(Math.round(usedMemory * 100.0) / 100.0)
                                        .usedMemoryPeak(Math.round(usedMemoryPeak * 100.0) / 100.0)
                                        .keyCount(dbSize)
                                        .hits(hits)
                                        .misses(misses)
                                        .hitRate(Math.round(hitRate * 100.0) / 100.0)
                                        .connectedClients(connectedClients)
                                        .build();
                            });
                },
                ReactiveRedisConnection::closeLater
        ).onErrorResume(e -> {
            log.warn("获取Redis缓存统计失败: {}", e.getMessage());
            return Mono.just(getDefaultRedisCacheStats());
        });
    }

    private SystemInfoVO.CacheStats getDefaultCacheStats() {
        return SystemInfoVO.CacheStats.builder()
                .redis(getDefaultRedisCacheStats())
                .caffeine(getDefaultCaffeineCacheStats())
                .build();
    }

    private SystemInfoVO.RedisCacheStats getDefaultRedisCacheStats() {
        return SystemInfoVO.RedisCacheStats.builder()
                .status("disconnected")
                .usedMemory(0.0)
                .usedMemoryPeak(0.0)
                .keyCount(0L)
                .hits(0L)
                .misses(0L)
                .hitRate(0.0)
                .connectedClients(0)
                .build();
    }

    private SystemInfoVO.CaffeineCacheStats getDefaultCaffeineCacheStats() {
        return SystemInfoVO.CaffeineCacheStats.builder()
                .cacheCount(0)
                .totalHits(0L)
                .totalMisses(0L)
                .totalHitRate(0.0)
                .totalSize(0L)
                .totalEvictions(0L)
                .build();
    }

    @Override
    @Cacheable(value = "monitor:threads", unless = "#result == null")
    public Flux<ThreadInfoVO> getAllThreads() {
        return Flux.defer(() -> {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long[] threadIds = threadMXBean.getAllThreadIds();
            java.lang.management.ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadIds, 20);
            
            List<ThreadInfoVO> threads = new ArrayList<>();
            for (java.lang.management.ThreadInfo info : threadInfos) {
                if (info != null) {
                    threads.add(buildThreadInfoVO(info, threadMXBean));
                }
            }
            return Flux.fromIterable(threads);
        });
    }

    @Override
    @Cacheable(value = "monitor:thread-stats", unless = "#result == null")
    public Mono<ThreadStatsVO> getThreadStats() {
        return Mono.fromCallable(() -> {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            Map<String, Integer> stateCount = new HashMap<>();
            long[] threadIds = threadMXBean.getAllThreadIds();
            
            List<ThreadInfoVO> allThreads = new ArrayList<>();
            for (long threadId : threadIds) {
                java.lang.management.ThreadInfo threadInfo = threadMXBean.getThreadInfo(threadId);
                if (threadInfo != null) {
                    String state = threadInfo.getThreadState().name();
                    stateCount.put(state, stateCount.getOrDefault(state, 0) + 1);
                    allThreads.add(buildThreadInfoVO(threadInfo, threadMXBean));
                }
            }

            List<ThreadInfoVO> topThreads = allThreads.stream()
                    .filter(t -> t.getCpuTime() != null && t.getCpuTime() > 0)
                    .sorted((t1, t2) -> Long.compare(t2.getCpuTime(), t1.getCpuTime()))
                    .limit(10)
                    .collect(Collectors.toList());

            long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
            List<Long> deadlockedIds = deadlockedThreads != null 
                    ? Arrays.stream(deadlockedThreads).boxed().collect(Collectors.toList())
                    : Collections.emptyList();

            return ThreadStatsVO.builder()
                    .totalThreadCount(threadMXBean.getThreadCount())
                    .daemonThreadCount(threadMXBean.getDaemonThreadCount())
                    .peakThreadCount(threadMXBean.getPeakThreadCount())
                    .activeThreadCount(threadMXBean.getThreadCount())
                    .threadStateCount(stateCount)
                    .topThreadsByCpu(topThreads)
                    .totalStartedThreadCount(threadMXBean.getTotalStartedThreadCount())
                    .deadlockedThreadIds(deadlockedIds)
                    .build();
        });
    }

    private double getCachedCpuUsage() {
        CpuUsageCache cache = cpuUsageCache.get();
        return cache != null ? cache.usage : 0.0;
    }

    private SystemInfoVO.GCInfo getGCInfo() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        long youngGcCount = 0, youngGcTime = 0, fullGcCount = 0, fullGcTime = 0;
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String name = gcBean.getName().toLowerCase();
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            
            if (name.contains("young") || name.contains("scavenge") || name.contains("parnew") || name.contains("copy")) {
                youngGcCount += count;
                youngGcTime += time;
            } else if (name.contains("old") || name.contains("marksweep") || name.contains("cms") || name.contains("g1")) {
                fullGcCount += count;
                fullGcTime += time;
            }
        }
        
        return SystemInfoVO.GCInfo.builder()
                .youngGcCount(youngGcCount)
                .youngGcTime(youngGcTime)
                .fullGcCount(fullGcCount)
                .fullGcTime(fullGcTime)
                .totalGcCount(youngGcCount + fullGcCount)
                .totalGcTime(youngGcTime + fullGcTime)
                .build();
    }

    private SystemInfoVO.HealthStatus calculateHealthStatus(double memoryUsage, double cpuUsage, SystemInfoVO.GCInfo gcInfo) {
        boolean frequentFullGc = gcInfo.getFullGcCount() > 100;
        
        if (memoryUsage > 90 || cpuUsage > 90 || frequentFullGc) {
            return SystemInfoVO.HealthStatus.DANGER;
        } else if (memoryUsage > 75 || cpuUsage > 75) {
            return SystemInfoVO.HealthStatus.WARNING;
        } else {
            return SystemInfoVO.HealthStatus.HEALTHY;
        }
    }

    private List<SystemInfoVO.DiskInfo> getDiskInfo() {
        List<SystemInfoVO.DiskInfo> diskInfos = new ArrayList<>();
        try {
            FileSystem fileSystem = os.getFileSystem();
            List<OSFileStore> fileStores = fileSystem.getFileStores();
            
            for (OSFileStore store : fileStores) {
                long totalSpace = store.getTotalSpace();
                long usableSpace = store.getUsableSpace();
                long usedSpace = totalSpace - usableSpace;
                
                if (totalSpace > 0 && !isVirtualFileSystem(store.getType())) {
                    double usagePercent = (double) usedSpace / totalSpace * 100;
                    
                    SystemInfoVO.DiskInfo diskInfo = SystemInfoVO.DiskInfo.builder()
                            .mountPoint(store.getMount())
                            .fileSystem(store.getType())
                            .totalSpace(totalSpace / GB)
                            .freeSpace(usableSpace / GB)
                            .usedSpace(usedSpace / GB)
                            .usagePercent(Math.round(usagePercent * 100.0) / 100.0)
                            .build();
                    
                    diskInfos.add(diskInfo);
                }
            }
        } catch (Exception e) {
            log.warn("获取磁盘信息失败: {}", e.getMessage());
        }
        return diskInfos;
    }

    private boolean isVirtualFileSystem(String fsType) {
        String type = fsType.toLowerCase();
        return type.contains("tmpfs") || type.contains("devfs") || type.contains("sysfs") || 
               type.contains("proc") || type.contains("devtmpfs") || type.contains("overlay");
    }

    private ThreadInfoVO buildThreadInfoVO(java.lang.management.ThreadInfo info, ThreadMXBean threadMXBean) {
        List<String> stackTrace = Arrays.stream(info.getStackTrace())
                .limit(20)
                .map(StackTraceElement::toString)
                .collect(Collectors.toList());

        List<String> lockedMonitors = Arrays.stream(info.getLockedMonitors())
                .map(monitor -> monitor.getClassName() + "@" + monitor.getIdentityHashCode())
                .collect(Collectors.toList());

        List<String> lockedSynchronizers = Arrays.stream(info.getLockedSynchronizers())
                .map(sync -> sync.getClassName() + "@" + sync.getIdentityHashCode())
                .collect(Collectors.toList());

        Long cpuTime = null, userTime = null;
        try {
            if (threadMXBean.isThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled()) {
                cpuTime = threadMXBean.getThreadCpuTime(info.getThreadId());
                userTime = threadMXBean.getThreadUserTime(info.getThreadId());
            }
        } catch (Exception e) {
            log.debug("无法获取线程CPU时间: {}", e.getMessage());
        }

        boolean daemon = false;
        int priority = Thread.NORM_PRIORITY;
        String threadGroupName = "unknown";
        
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        
        Thread[] threads = new Thread[rootGroup.activeCount()];
        rootGroup.enumerate(threads);
        for (Thread t : threads) {
            if (t != null && t.getId() == info.getThreadId()) {
                daemon = t.isDaemon();
                priority = t.getPriority();
                threadGroupName = t.getThreadGroup() != null ? t.getThreadGroup().getName() : "unknown";
                break;
            }
        }

        return ThreadInfoVO.builder()
                .threadId(info.getThreadId())
                .threadName(info.getThreadName())
                .threadState(info.getThreadState().name())
                .daemon(daemon)
                .priority(priority)
                .threadGroupName(threadGroupName)
                .stackTrace(stackTrace)
                .lockedMonitors(lockedMonitors)
                .lockedSynchronizers(lockedSynchronizers)
                .cpuTime(cpuTime)
                .userTime(userTime)
                .build();
    }

    private SystemInfoVO.ClassLoadingInfo getClassLoadingInfo() {
        try {
            ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
            return SystemInfoVO.ClassLoadingInfo.builder()
                    .loadedClassCount(classLoadingMXBean.getLoadedClassCount())
                    .totalLoadedClassCount(classLoadingMXBean.getTotalLoadedClassCount())
                    .unloadedClassCount(classLoadingMXBean.getUnloadedClassCount())
                    .build();
        } catch (Exception e) {
            log.warn("获取类加载信息失败: {}", e.getMessage());
            return SystemInfoVO.ClassLoadingInfo.builder()
                    .loadedClassCount(0)
                    .totalLoadedClassCount(0L)
                    .unloadedClassCount(0L)
                    .build();
        }
    }

    private SystemInfoVO.CaffeineCacheStats getCaffeineCacheStats() {
        try {
            Collection<String> cacheNames = cacheManager.getCacheNames();
            long totalHits = 0, totalMisses = 0, totalSize = 0, totalEvictions = 0;
            int cacheCount = 0;

            for (String cacheName : cacheNames) {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                if (cache instanceof CaffeineCache caffeineCache) {
                    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                    com.github.benmanes.caffeine.cache.stats.CacheStats stats = nativeCache.stats();
                    
                    totalHits += stats.hitCount();
                    totalMisses += stats.missCount();
                    totalSize += nativeCache.estimatedSize();
                    totalEvictions += stats.evictionCount();
                    cacheCount++;
                }
            }

            double totalHitRate = (totalHits + totalMisses) > 0 
                    ? (double) totalHits / (totalHits + totalMisses) * 100 
                    : 0.0;

            return SystemInfoVO.CaffeineCacheStats.builder()
                    .cacheCount(cacheCount)
                    .totalHits(totalHits)
                    .totalMisses(totalMisses)
                    .totalHitRate(Math.round(totalHitRate * 100.0) / 100.0)
                    .totalSize(totalSize)
                    .totalEvictions(totalEvictions)
                    .build();
        } catch (Exception e) {
            log.warn("获取Caffeine缓存统计失败: {}", e.getMessage());
            return SystemInfoVO.CaffeineCacheStats.builder()
                    .cacheCount(0)
                    .totalHits(0L)
                    .totalMisses(0L)
                    .totalHitRate(0.0)
                    .totalSize(0L)
                    .totalEvictions(0L)
                    .build();
        }
    }

    private SystemInfoVO.DataSourceStats getDataSourceStats() {
        try {
            // Query R2DBC connection pool metrics
            // Note: R2DBC doesn't expose pool metrics directly, we need to query from the pool implementation
            // This is a simplified version - actual implementation depends on the pool library (r2dbc-pool)
            
            // For now, return placeholder values
            // In production, you would integrate with r2dbc-pool metrics or use Micrometer metrics
            return SystemInfoVO.DataSourceStats.builder()
                    .activeConnections(0)
                    .idleConnections(0)
                    .totalConnections(0)
                    .maxConnections(10)
                    .minIdle(5)
                    .pendingThreads(0)
                    .build();
        } catch (Exception e) {
            log.warn("获取数据源统计失败: {}", e.getMessage());
            return SystemInfoVO.DataSourceStats.builder()
                    .activeConnections(0)
                    .idleConnections(0)
                    .totalConnections(0)
                    .maxConnections(0)
                    .minIdle(0)
                    .pendingThreads(0)
                    .build();
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
