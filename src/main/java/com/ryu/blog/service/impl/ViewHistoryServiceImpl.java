package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.ViewHistoryDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.entity.ViewHistory;
import com.ryu.blog.mapper.ViewHistoryMapper;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.repository.ViewHistoryRepository;
import com.ryu.blog.service.ViewHistoryService;
import com.ryu.blog.utils.IpUtil;
import com.ryu.blog.utils.IPLocationUtil;
import com.ryu.blog.utils.UserAgentAnalyzer;
import com.ryu.blog.vo.ViewHistoryStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 浏览历史服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewHistoryServiceImpl implements ViewHistoryService {

    private final ViewHistoryRepository viewHistoryRepository;
    private final PostsRepository postsRepository;
    private final UserRepository userRepository;
    private final ViewHistoryMapper viewHistoryMapper;
    private final CacheManager cacheManager;

    /**
     * 自身代理 —— 让类内部的调用也能命中 {@code @Cacheable}。
     *
     * <p>{@code getViewHistoryStats()} 内部要读总访问量，而
     * {@code getTotalViewsFromDatabase()} 带 {@code @Cacheable}。直接写方法名是
     * <b>自调用</b>：Spring AOP 的 CGLIB 代理里，目标对象内部的 {@code this}
     * 指向目标实例而不是代理，拦截器不触发 —— 总访问量每次都要 COUNT 一遍全表。
     *
     * <p>{@code @Lazy} 用来打破「代理依赖自己」的循环依赖。
     */
    private ViewHistoryService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy ViewHistoryService self) {
        this.self = self;
    }

    // 设备和地区分布统计 - 使用LRU限制大小，防止内存泄漏
    private final Map<String, Integer> deviceStats = Collections.synchronizedMap(new LinkedHashMap<String, Integer>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 100;
        }
    });
    
    private final Map<String, Integer> locationStats = Collections.synchronizedMap(new LinkedHashMap<String, Integer>(500, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 500;
        }
    });
    
    // 记录同一用户访问同一文章的时间间隔（分钟）
    private static final int VISIT_INTERVAL_MINUTES = 30;
    // 浏览记录缓存键前缀
    private static final String VISIT_RECORD_KEY_PREFIX = CacheConstants.VIEW_CACHE_PREFIX + "record:";
    // 文章浏览量缓存键前缀
    private static final String ARTICLE_VIEW_COUNT_KEY = CacheConstants.VIEW_COUNT_KEY;
    // 访问记录缓存名称 - 使用常量
    private static final String VISIT_RECORD_CACHE = CacheConstants.VISIT_RECORD_CACHE;
    // 同步到数据库的阈值
    private static final int SYNC_THRESHOLD = 10;

    /**
     * 浏览量增量的回写计划。
     *
     * @param newCount      本次自增后的缓存计数
     * @param deltaToFlush 需要回写数据库的增量（{@code null} = 本次不需要回写）
     * @param cacheMissing 缓存不可用 → 直接 UPDATE 数据库
     */
    private record ViewCountPlan(int newCount, Integer deltaToFlush, boolean cacheMissing) {

        static ViewCountPlan missing() {
            return new ViewCountPlan(0, null, true);
        }

        static ViewCountPlan of(int newCount) {
            return new ViewCountPlan(newCount, null, false);
        }

        static ViewCountPlan flush(int newCount, int delta) {
            return new ViewCountPlan(newCount, delta, false);
        }
    }

    /**
     * 记录一次浏览。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：这里要做的事是「插一条浏览记录」
     * +「把浏览量增量批量回写」，两者不要求原子 —— 浏览量本来就是最终一致的
     * （缓存里攒够 {@link #SYNC_THRESHOLD} 次才回写一次）。
     *
     * <p>加了事务反而有两个坑：
     * <ol>
     *   <li>事务会把整条链绑到同一条 R2DBC 连接上，而链中间的缓存操作要切到
     *       {@code boundedElastic} 线程做（{@code Mono.fromCallable}），
     *       事务连接会被占着；</li>
     *   <li>浏览量回写原本是 fire-and-forget（另起一次订阅），
     *       那次订阅拿不到事务上下文，会<b>在事务持有连接的同时再向连接池借一条</b> ——
     *       这正是 CLAUDE.md 里那条「事务内禁止并发数据库操作」的由来。
     *       现在回写改成链上的一步，不再另起订阅。</li>
     * </ol>
     */
    @Override
    public Mono<Boolean> addViewHistory(ViewHistoryDTO viewHistoryDTO, Long userId) {
        log.debug("添加浏览历史: {}", viewHistoryDTO);
        
        if (viewHistoryDTO == null || viewHistoryDTO.getPostId() == null) {
            log.warn("添加浏览历史失败: 文章ID为空");
            return Mono.just(false);
        }
        
        Long articleId = viewHistoryDTO.getPostId();
        String visitorId = viewHistoryDTO.getVisitorId();
        
        if (visitorId == null || visitorId.isEmpty()) {
            log.warn("添加浏览历史失败: 访客ID为空");
            return Mono.just(false);
        }
        
        // 构建访问记录缓存键
        String visitRecordKey = VISIT_RECORD_KEY_PREFIX + visitorId + ":" + articleId;
        
        // 从当前请求上下文获取IP地址、设备信息和地理位置
        return Mono.deferContextual(contextView -> {
            // 尝试从上下文中获取ServerWebExchange对象
            Optional<ServerWebExchange> exchangeOptional = contextView.getOrEmpty(ServerWebExchange.class);
            
            // 获取IP地址
            String ipAddress = "";
            // 获取用户代理信息
            String userAgent = "";
            // 获取地理位置
            String location = "";
            // 设备信息
            String deviceInfo = "";
            
            // 如果存在ServerWebExchange，则获取请求信息
            if (exchangeOptional.isPresent()) {
                ServerWebExchange exchange = exchangeOptional.get();
                ServerHttpRequest request = exchange.getRequest();
                
                // 获取IP地址
                ipAddress = IpUtil.getClientIp(exchange);
                
                // 获取用户代理信息
                userAgent = request.getHeaders().getFirst("User-Agent");
                
                // 使用新的UserAgentAnalyzer解析设备信息
                deviceInfo = UserAgentAnalyzer.formatDeviceInfo(userAgent);
                
                // 获取地理位置
                location = IPLocationUtil.getIpLocation(ipAddress);
            }
            
            final String finalIpAddress = ipAddress;
            final String finalUserAgent = userAgent;
            final String finalLocation = location;
            final String finalDeviceInfo = deviceInfo;
            
            // 1. 首先检查缓存中是否存在访问记录以及访问时间是否在限定间隔内
            return Mono.fromCallable(() -> {
                Cache visitRecordCache = cacheManager.getCache(VISIT_RECORD_CACHE);
                boolean isNewVisit = true;
                
                if (visitRecordCache != null) {
                    ValueWrapper wrapper = visitRecordCache.get(visitRecordKey);
                    if (wrapper != null) {
                        // 如果记录存在，则不增加浏览量，但仍然记录浏览历史
                        log.debug("短时间内重复访问: 文章ID={}, 访客ID={}, 不增加浏览量", articleId, visitorId);
                        // 更新缓存中的时间戳
                        visitRecordCache.put(visitRecordKey, System.currentTimeMillis());
                        isNewVisit = false;
                    } else {
                        // 如果记录不存在，则设置一个新的访问记录
                        visitRecordCache.put(visitRecordKey, System.currentTimeMillis());
                    }
                }
                
                return isNewVisit;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(isNewVisit -> {
                // 2. 创建新的浏览记录（无论是否存在历史记录）
                ViewHistory history = new ViewHistory();
                history.setPostId(articleId);
                history.setVisitorId(visitorId);
                history.setUserId(userId);
                history.setViewTime(LocalDateTime.now());
                history.setCreateTime(LocalDateTime.now());
                history.setUpdateTime(LocalDateTime.now());
                
                // 设置额外信息
                history.setViewDuration(viewHistoryDTO.getViewDuration());
                history.setReferer(viewHistoryDTO.getReferrer());
                
                // 设置IP地址、设备信息和地理位置
                history.setIpAddress(finalIpAddress);
                history.setAgent(finalDeviceInfo); // 使用格式化后的设备信息
                history.setLocation(finalLocation);
                
                // 3. 保存浏览记录
                return viewHistoryRepository.save(history)
                    .flatMap(savedHistory -> {
                        // 4. 如果是新访问（间隔超过30分钟），则增加文章浏览量
                        if (isNewVisit) {
                            // 更新缓存统计数据
                            updateCacheStats(articleId, visitorId);
                            
                            // 更新设备和地区统计
                            if (!finalUserAgent.isEmpty()) {
                                updateDeviceStats(finalUserAgent);
                            }
                            
                            if (!finalLocation.isEmpty()) {
                                updateLocationStats(finalLocation);
                            }
                            
                            return incrementArticleViewCount(articleId).thenReturn(true);
                        }
                        return Mono.just(false);
                    });
            });
        })
        .doOnSuccess(result -> {
            if (result) {
                log.debug("添加浏览历史成功并增加浏览量: 文章ID={}, 访客ID={}", articleId, visitorId);
            } else {
                log.debug("添加浏览历史成功但不增加浏览量: 文章ID={}, 访客ID={}", articleId, visitorId);
            }
        })
        .doOnError(e -> log.error("添加浏览历史失败: 文章ID={}, 访客ID={}",
                articleId, visitorId, e));
    }

    /**
     * 更新设备统计信息
     * 
     * @param agent 设备信息
     */
    private void updateDeviceStats(String agent) {
        if (agent == null || agent.isEmpty()) {
            return;
        }
        
        // 使用UserAgentAnalyzer获取设备类型
        String deviceType = UserAgentAnalyzer.getDeviceType(agent);
        deviceStats.compute(deviceType, (k, v) -> (v == null) ? 1 : v + 1);
    }
    
    /**
     * 更新地区统计信息
     * 
     * @param location 地理位置信息
     */
    private void updateLocationStats(String location) {
        if (location == null || location.isEmpty()) {
            return;
        }
        
        locationStats.compute(location, (k, v) -> (v == null) ? 1 : v + 1);
    }

    /**
     * 增加文章浏览量 - 使用Caffeine缓存
     * 采用缓存计数器，定期同步到数据库
     */
    private Mono<Integer> incrementArticleViewCount(Long articleId) {
        if (articleId == null) {
            return Mono.just(0);
        }

        return Mono.fromCallable(() -> {
            Cache viewCountCache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE);
            if (viewCountCache == null) {
                log.warn("浏览量缓存不可用，将直接更新数据库");
                return ViewCountPlan.missing();
            }

            String cacheKey = ARTICLE_VIEW_COUNT_KEY + articleId;

            // 使用computeIfAbsent模式避免竞态条件
            Object nativeCache = viewCountCache.getNativeCache();
            if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
                @SuppressWarnings("unchecked")
                com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache =
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache;

                AtomicInteger counter = (AtomicInteger) caffeineCache.get(cacheKey, k -> new AtomicInteger(0));
                int newCount = counter.incrementAndGet();

                // 每积累 SYNC_THRESHOLD 次浏览就把增量回写数据库，减少数据库写入次数。
                // 缓存里保存的是「尚未回写的增量」，不是文章的总浏览量，
                // 因此回写必须用累加（addViews），用覆盖（updateViews）会把历史浏览量清零。
                if (newCount >= SYNC_THRESHOLD) {
                    int delta = counter.getAndSet(0);
                    if (delta > 0) {
                        log.debug("回写浏览量增量到数据库: ID={}, 增量={}", articleId, delta);
                        return ViewCountPlan.flush(newCount, delta);
                    }
                }

                return ViewCountPlan.of(newCount);
            }

            return ViewCountPlan.missing(); // 缓存类型不匹配
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(plan -> {
            if (plan.cacheMissing()) {
                // 缓存不可用，直接更新数据库
                return postsRepository.incrementViews(articleId)
                    .defaultIfEmpty(0);
            }
            if (plan.deltaToFlush() != null) {
                // 回写是链上的一步，**不是 fire-and-forget**：
                // 另起订阅会脱离当前链的事务/连接上下文，在事务仍持有连接时
                // 再向连接池借一条 —— 这正是「Request queue is full」的温床。
                final int delta = plan.deltaToFlush();
                return flushArticleViewDelta(articleId, delta)
                    .onErrorResume(e -> {
                        // 回写失败：把增量放回缓存等下一轮重试。
                        // 直接吞掉的话，这篇文章的浏览量会永久少掉这一段。
                        log.error("回写浏览量增量到数据库失败: ID={}, 增量={}", articleId, delta, e);
                        restorePendingDelta(articleId, delta);
                        return Mono.just(0);
                    })
                    .thenReturn(plan.newCount());
            }
            return Mono.just(plan.newCount());
        })
        .onErrorResume(e -> {
            log.error("增加文章浏览量失败: ID={}", articleId, e);
            // 降级：尝试直接更新数据库
            return postsRepository.incrementViews(articleId)
                .defaultIfEmpty(0)
                .onErrorReturn(0);
        });
    }

    /**
     * 把缓存中的浏览量增量回写到数据库（累加，不覆盖）。
     *
     * @param articleId 文章ID
     * @param delta     增量
     * @return 受影响行数
     */
    private Mono<Integer> flushArticleViewDelta(Long articleId, int delta) {
        return postsRepository.addViews(articleId, delta)
            .doOnSuccess(result -> {
                if (result != null && result > 0) {
                    log.debug("数据库浏览量增量更新成功: ID={}, 增量={}", articleId, delta);
                } else {
                    log.warn("数据库浏览量更新无影响: ID={}, 可能文章不存在", articleId);
                }
            })
            .doOnError(e -> log.error("同步浏览量到数据库失败: ID={}, 增量={}", articleId, delta, e));
    }

    /**
     * 回写失败时把增量还回缓存计数器，等待下一轮重试。
     *
     * @param articleId 文章ID
     * @param delta     需要还回的增量
     */
    private void restorePendingDelta(Long articleId, int delta) {
        if (delta <= 0) {
            return;
        }
        Cache viewCountCache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE);
        if (viewCountCache == null) {
            return;
        }
        Object nativeCache = viewCountCache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache =
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache;
            AtomicInteger counter = (AtomicInteger) caffeineCache.get(
                ARTICLE_VIEW_COUNT_KEY + articleId, k -> new AtomicInteger(0));
            counter.addAndGet(delta);
        }
    }

    @Override
    public Flux<ViewHistory> getUserViewHistory(Long userId) {
        return viewHistoryRepository.findByUserIdOrderByCreateTimeDesc(userId);
    }

    @Override
    public Mono<Map<String, Object>> getUserViewHistoryPaged(int page, int size, Long userId) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;

        final int finalPage = page;
        final int finalSize = size;

        return viewHistoryRepository.countByUserId(userId)
                .flatMap(total -> {
                    long offset = (finalPage - 1) * finalSize;
                    return viewHistoryRepository.findUserHistoryPaged(userId, finalSize, offset)
                            .collectList()
                            // 一次把本页涉及的文章全部查出来，避免每条历史再查一次文章（N+1）
                            .flatMap(histories -> {
                                List<Long> postIds = histories.stream()
                                        .map(ViewHistory::getPostId)
                                        .filter(Objects::nonNull)
                                        .distinct()
                                        .toList();
                                if (postIds.isEmpty()) {
                                    return Mono.just(histories.stream()
                                            .map(history -> viewHistoryMapper.toViewHistoryVO(history))
                                            .toList());
                                }
                                return postsRepository.findByIdIn(postIds)
                                        .collectMap(Posts::getId, post -> post)
                                        .map(postMap -> histories.stream()
                                                .map(history -> postMap.containsKey(history.getPostId())
                                                        ? viewHistoryMapper.toViewHistoryVO(history, postMap.get(history.getPostId()))
                                                        : viewHistoryMapper.toViewHistoryVO(history))
                                                .toList());
                            })
                            .map(histories -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("records", histories);
                                result.put("total", total);
                                result.put("pages", (total + finalSize - 1) / finalSize);
                                result.put("current", finalPage);
                                return result;
                            });
                });
    }

    @Override
    @Cacheable(cacheNames = CacheConstants.VIEW_HISTORY_POST_PV_CACHE, key = "'pv:' + #articleId", unless = "#result == 0")
    public Mono<Long> getArticleViewCount(Long articleId) {
        log.debug("从数据库获取文章访问量: articleId={}", articleId);
        // 统一以 t_posts.views 为准：
        // 浏览记录条数（t_view_history 的 COUNT）与真实浏览量不是一回事 ——
        // 30 分钟内的重复访问会写记录但不计数，两套口径会让前台 PV 与列表页对不上。
        return postsRepository.findById(articleId)
                .map(post -> post.getViews() != null ? (long) post.getViews() : 0L)
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Map<Long, Long>> batchGetArticleViewCounts(Iterable<Long> articleIds) {
        // 空集合必须提前挡掉：底层 SQL 是 `id IN (:ids)`，集合为空时驱动会把占位符
        // 展开成 `IN ()` —— MySQL 报语法错误 1064。这个接口是 REST 暴露的
        // （POST /view-history/batch-count），客户端传空数组就会把请求打成 500。
        if (articleIds == null || !articleIds.iterator().hasNext()) {
            return Mono.just(Collections.emptyMap());
        }

        return postsRepository.findViewsByIds(articleIds)
                .collectMap(
                        objects -> (Long) objects[0],  // articleId
                        objects -> objects[1] != null ? ((Number) objects[1]).longValue() : 0L
                )
                .defaultIfEmpty(Collections.emptyMap());
    }

    @Override
    @Transactional
    public Mono<Boolean> clearUserViewHistory(Long userId) {
        return viewHistoryRepository.deleteByUserId(userId)
                .then(Mono.just(true))
                .onErrorReturn(false);
    }

    @Override
    public Mono<ViewHistoryStatsVO> getViewHistoryStats() {
        log.info("开始获取浏览历史统计信息");
        
        // 创建返回对象
        ViewHistoryStatsVO statsVO = new ViewHistoryStatsVO();
        
        // 获取日期格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String today = LocalDate.now().format(formatter);
        String yesterday = LocalDate.now().minusDays(1).format(formatter);
        
        // 1. 获取今日和昨日PV
        Long todayPv = getCachedPvValue(CacheConstants.VIEW_HISTORY_PV_KEY_PREFIX + today);
        Long yesterdayPv = getCachedPvValue(CacheConstants.VIEW_HISTORY_PV_KEY_PREFIX + yesterday);
        
        statsVO.setTodayViews(todayPv != null ? todayPv : 0L);
        statsVO.setYesterdayViews(yesterdayPv != null ? yesterdayPv : 0L);
        
        // 2. 获取每日趋势数据
        Map<String, Long> dailyViewsTrend = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).format(formatter);
            Long datePv = getCachedPvValue(CacheConstants.VIEW_HISTORY_PV_KEY_PREFIX + date);
            dailyViewsTrend.put(date, datePv != null ? datePv : 0L);
        }
        statsVO.setDailyViewsTrend(dailyViewsTrend);
        
        // 3. 计算本周和本月PV
        long weeklyPv = dailyViewsTrend.values().stream().mapToLong(Long::longValue).sum();
        statsVO.setWeeklyViews(weeklyPv);
        
        long monthlyPv = 0L;
        for (int i = 0; i < 30; i++) {
            String date = LocalDate.now().minusDays(i).format(formatter);
            Long datePv = getCachedPvValue(CacheConstants.VIEW_HISTORY_PV_KEY_PREFIX + date);
            if (datePv != null) {
                monthlyPv += datePv;
            }
        }
        statsVO.setMonthlyViews(monthlyPv);
        
        // 4. 获取今日UV
        Set<String> uniqueVisitors = getCachedUvValue(CacheConstants.VIEW_HISTORY_UV_KEY_PREFIX + today);
        statsVO.setUniqueVisitors(uniqueVisitors != null ? (long) uniqueVisitors.size() : 0L);
        
        // 5. 获取访问量最高的文章
        Map<Long, Integer> topPosts = new LinkedHashMap<>();
        
        // 从缓存中获取文章访问量
        org.springframework.cache.Cache postPvCacheObj = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE);
        if (postPvCacheObj != null) {
            Object nativeCache = postPvCacheObj.getNativeCache();
            if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> cacheMap = ((com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache).asMap();
                
                cacheMap.entrySet().stream()
                        .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof Long)
                        .sorted(Map.Entry.<Object, Object>comparingByValue((o1, o2) -> ((Long) o2).compareTo((Long) o1)))
                        .limit(5)
                        .forEach(entry -> {
                            String key = (String) entry.getKey();
                            if (key.startsWith(CacheConstants.VIEW_HISTORY_POST_PV_KEY_PREFIX)) {
                                String postIdStr = key.substring(CacheConstants.VIEW_HISTORY_POST_PV_KEY_PREFIX.length());
                                try {
                                    Long postId = Long.parseLong(postIdStr);
                                    topPosts.put(postId, ((Long) entry.getValue()).intValue());
                                } catch (NumberFormatException e) {
                                    log.warn("无法解析文章ID: {}", postIdStr);
                                }
                            }
                        });
            }
        }
        
        // 如果缓存中没有足够的数据，从数据库补充
        if (topPosts.size() < 5) {
            return getTopPostsFromDatabase(5 - topPosts.size())
                    .map(dbTopPosts -> {
                        // 合并缓存和数据库的结果
                        topPosts.putAll(dbTopPosts);
                        statsVO.setTopPosts(topPosts);
                        
                        // 设置设备和地区分布
                        statsVO.setDeviceDistribution(new HashMap<>(deviceStats));
                        statsVO.setLocationDistribution(new HashMap<>(locationStats));
                        
                        // 获取总访问量（走 self 代理才会命中 @Cacheable）
                        return self.getTotalViewsFromDatabase()
                                .doOnNext(statsVO::setTotalViews)
                                .thenReturn(statsVO);
                    })
                    .flatMap(mono -> mono);
        }
        
        // 设置访问量最高的文章
        statsVO.setTopPosts(topPosts);
        
        // 设置设备和地区分布
        statsVO.setDeviceDistribution(new HashMap<>(deviceStats));
        statsVO.setLocationDistribution(new HashMap<>(locationStats));
        
        // 获取总访问量（走 self 代理才会命中 @Cacheable）
        return self.getTotalViewsFromDatabase()
                .doOnNext(statsVO::setTotalViews)
                .thenReturn(statsVO)
                .doOnSuccess(stats -> log.info("浏览历史统计信息获取完成: 总访问量={}, 今日访问量={}, 独立访客数={}",
                        stats.getTotalViews(), stats.getTodayViews(), stats.getUniqueVisitors()));
    }
    
    /**
     * 从数据库获取访问量最高的文章
     * 
     * @param limit 获取数量
     * @return 文章ID和访问量的映射
     */
    private Mono<Map<Long, Integer>> getTopPostsFromDatabase(int limit) {
        // 用带 LIMIT 的查询取热门文章；
        // findAll(Sort).take(limit) 会把整张 t_posts 表全部拉进内存，只为了取前 5 条。
        return postsRepository.findHotPosts(limit)
                .collectMap(
                        Posts::getId,
                        post -> post.getViews() != null ? post.getViews() : 0
                )
                .defaultIfEmpty(Collections.emptyMap());
    }
    
    /**
     * 从数据库获取总访问量
     * 
     * @return 总访问量
     */
    @Override
    @Cacheable(cacheNames = CacheConstants.VIEW_HISTORY_PV_CACHE, key = "'" + CacheConstants.STATS_TOTAL_KEY + "'", unless = "#result == 0")
    public Mono<Long> getTotalViewsFromDatabase() {
        log.debug("从数据库获取总访问量");
        return viewHistoryRepository.count();
    }
    
    /**
     * 更新缓存中的统计数据
     */
    private void updateCacheStats(Long articleId, String visitorId) {
        // 获取当前日期
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // 更新PV
        String pvKey = CacheConstants.VIEW_HISTORY_PV_KEY_PREFIX + today;
        updatePvValue(pvKey);
        
        // 更新文章PV
        String postPvKey = CacheConstants.VIEW_HISTORY_POST_PV_KEY_PREFIX + articleId;
        updatePostPvValue(postPvKey);
        
        // 更新UV
        String uvKey = CacheConstants.VIEW_HISTORY_UV_KEY_PREFIX + today;
        updateUvValue(uvKey, visitorId);
    }
    
    /**
     * 获取缓存中的PV值
     */
    private Long getCachedPvValue(String key) {
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_PV_CACHE);
        if (cache != null) {
            ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                Object value = wrapper.get();
                if (value instanceof Long) {
                    return (Long) value;
                }
            }
        }
        return 0L;
    }
    
    /**
     * 获取缓存中的UV集合
     */
    private Set<String> getCachedUvValue(String key) {
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_UV_CACHE);
        if (cache != null) {
            ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                Object value = wrapper.get();
                if (value instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> visitors = (Set<String>) value;
                    return visitors;
                }
            }
        }
        return new HashSet<>();
    }
    
    /**
     * 更新PV值 - 线程安全的原子操作
     */
    private void updatePvValue(String key) {
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_PV_CACHE);
        if (cache == null) {
            log.warn("PV缓存不可用: {}", key);
            return;
        }
        
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = 
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache;
            
            caffeineCache.asMap().compute(key, (k, v) -> {
                if (v instanceof Long) {
                    return (Long) v + 1L;
                }
                return 1L;
            });
        }
    }
    
    /**
     * 更新文章PV值 - 线程安全的原子操作
     */
    private void updatePostPvValue(String key) {
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE);
        if (cache == null) {
            log.warn("文章PV缓存不可用: {}", key);
            return;
        }
        
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = 
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache;
            
            caffeineCache.asMap().compute(key, (k, v) -> {
                if (v instanceof Long) {
                    return (Long) v + 1L;
                }
                return 1L;
            });
        }
    }
    
    /**
     * 更新UV集合 - 线程安全的操作
     */
    private void updateUvValue(String key, String visitorId) {
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_UV_CACHE);
        if (cache == null) {
            log.warn("UV缓存不可用: {}", key);
            return;
        }
        
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = 
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache;
            
            caffeineCache.asMap().compute(key, (k, v) -> {
                @SuppressWarnings("unchecked")
                Set<String> visitors = (v instanceof Set) ? new HashSet<>((Set<String>) v) : new HashSet<>();
                visitors.add(visitorId);
                return visitors;
            });
        }
    }

    /**
     * 获取文章当前浏览量
     * <p>
     * = 数据库里已回写的浏览量 + 缓存里尚未回写的增量。
     * 缓存中只保存增量，直接把缓存值当作浏览量返回会得到一个远小于真实值的数字。
     *
     * @param articleId 文章ID
     * @return 浏览量
     */
    @Override
    public Mono<Long> getArticleCurrentViews(Long articleId) {
        if (articleId == null) {
            return Mono.just(0L);
        }

        return Mono.fromCallable(() -> {
            // 读取缓存中尚未回写的增量
            Cache viewCountCache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE);
            if (viewCountCache != null) {
                String cacheKey = ARTICLE_VIEW_COUNT_KEY + articleId;
                AtomicInteger counter = viewCountCache.get(cacheKey, AtomicInteger.class);
                if (counter != null) {
                    return (long) counter.get();
                }
            }
            return 0L;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(pendingDelta -> postsRepository.findById(articleId)
                .map(article -> (article.getViews() != null ? (long) article.getViews() : 0L) + pendingDelta)
                .defaultIfEmpty(pendingDelta))
        .doOnSuccess(views -> log.debug("获取文章当前浏览量: ID={}, 浏览量={}", articleId, views))
        .onErrorResume(e -> {
            log.error("获取文章浏览量失败: ID={}", articleId, e);
            return Mono.just(0L);
        });
    }

    /**
     * 批量同步缓存中的文章浏览量到数据库
     * 用于定时任务调用，确保数据库中的浏览量与缓存保持同步
     * 
     * @return 同步的文章数量
     */
    @Override
    public Mono<Integer> syncViewCountsToDatabase() {
        log.info("开始同步缓存中的文章浏览量到数据库");
        
        return Mono.fromCallable(() -> {
            Cache viewCountCache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE);
            if (viewCountCache == null) {
                log.warn("浏览量缓存不可用，跳过同步");
                return Collections.<Mono<Integer>>emptyList();
            }
            
            Object nativeCache = viewCountCache.getNativeCache();
            if (!(nativeCache instanceof com.github.benmanes.caffeine.cache.Cache)) {
                log.warn("缓存类型不匹配，跳过同步");
                return Collections.<Mono<Integer>>emptyList();
            }
            
            @SuppressWarnings("unchecked")
            Map<Object, Object> cacheMap = ((com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache).asMap();
            
            // 收集所有需要更新的操作
            List<Mono<Integer>> updateOperations = new ArrayList<>();
            
            cacheMap.forEach((key, value) -> {
                if (key instanceof String && value instanceof AtomicInteger) {
                    String cacheKey = (String) key;
                    if (cacheKey.startsWith(ARTICLE_VIEW_COUNT_KEY)) {
                        String articleIdStr = cacheKey.substring(ARTICLE_VIEW_COUNT_KEY.length());
                        try {
                            Long articleId = Long.parseLong(articleIdStr);
                            // 缓存里存的是尚未回写的增量，取走并清零；
                            // 回写用累加而不是覆盖，否则文章的历史浏览量会被清零。
                            int delta = ((AtomicInteger) value).getAndSet(0);
                            if (delta <= 0) {
                                return;
                            }

                            // 添加到批处理操作列表
                            updateOperations.add(
                                postsRepository.addViews(articleId, delta)
                                    .doOnSuccess(result -> {
                                        if (result > 0) {
                                            log.debug("同步文章浏览量增量成功: ID={}, 增量={}", articleId, delta);
                                        } else {
                                            log.warn("文章浏览量更新无影响: ID={}", articleId);
                                        }
                                    })
                                    .onErrorResume(e -> {
                                        log.error("同步文章浏览量失败: ID={}", articleId, e);
                                        // 回写失败时把增量放回缓存，等待下一轮重试
                                        ((AtomicInteger) value).addAndGet(delta);
                                        return Mono.just(0);
                                    })
                            );
                        } catch (NumberFormatException e) {
                            log.warn("无效的文章ID格式: {}", articleIdStr);
                        }
                    }
                }
            });
            
            return updateOperations;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(updateOperations -> {
            if (updateOperations.isEmpty()) {
                log.info("没有需要同步的文章浏览量");
                return Mono.just(0);
            }
            
            // 并发执行所有更新操作，并等待完成
            return Flux.merge(updateOperations)
                .filter(result -> result > 0) // 只统计成功的更新
                .count()
                .map(Long::intValue);
        })
        .doOnSuccess(count -> log.info("文章浏览量同步任务完成，成功同步 {} 篇文章", count))
        .onErrorResume(e -> {
            log.error("同步文章浏览量到数据库失败: {}", e.getMessage(), e);
            return Mono.just(0);
        });
    }
} 