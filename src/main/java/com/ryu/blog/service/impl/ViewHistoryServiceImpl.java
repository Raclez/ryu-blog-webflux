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
import org.springframework.data.domain.Sort;
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
@CacheConfig(cacheNames = {CacheConstants.VIEW_HISTORY_PV_CACHE_NAME, CacheConstants.VIEW_HISTORY_UV_CACHE_NAME, CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME})
public class ViewHistoryServiceImpl implements ViewHistoryService {

    private final ViewHistoryRepository viewHistoryRepository;
    private final PostsRepository postsRepository;
    private final UserRepository userRepository;
    private final ViewHistoryMapper viewHistoryMapper;
    private final CacheManager cacheManager;

    // 设备和地区分布统计
    private final Map<String, Integer> deviceStats = new ConcurrentHashMap<>();
    private final Map<String, Integer> locationStats = new ConcurrentHashMap<>();
    
    // 记录同一用户访问同一文章的时间间隔（分钟）
    private static final int VISIT_INTERVAL_MINUTES = 30;
    // 浏览记录缓存键前缀
    private static final String VISIT_RECORD_KEY_PREFIX = CacheConstants.VIEW_CACHE_PREFIX + "record:";
    // 文章浏览量缓存键前缀
    private static final String ARTICLE_VIEW_COUNT_KEY = CacheConstants.VIEW_COUNT_KEY;
    // 访问记录缓存名称 - 使用常量
    private static final String VISIT_RECORD_CACHE_NAME = CacheConstants.VISIT_RECORD_CACHE_NAME;

    @Override
    @Transactional
    public Mono<Boolean> addViewHistory(ViewHistoryDTO viewHistoryDTO) {
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
                Cache visitRecordCache = cacheManager.getCache(VISIT_RECORD_CACHE_NAME);
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
        .doOnError(e -> log.error("添加浏览历史失败: 文章ID={}, 访客ID={}, 错误={}", 
                articleId, visitorId, e.getMessage()));
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
            Cache viewCountCache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME);
            if (viewCountCache != null) {
                String cacheKey = ARTICLE_VIEW_COUNT_KEY + articleId;
                AtomicInteger counter = viewCountCache.get(cacheKey, AtomicInteger.class);
                
                if (counter == null) {
                    // 如果计数器不存在，创建一个新的计数器
                    counter = new AtomicInteger(1);
                    viewCountCache.put(cacheKey, counter);
                    return 1;
                } else {
                    // 原子递增
                    int newCount = counter.incrementAndGet();
                    
                    // 每10次浏览同步到数据库，减少数据库写入次数
                    if (newCount % 10 == 0) {
                        log.debug("同步浏览量到数据库: ID={}, 浏览量={}", articleId, newCount);
                        // 异步更新数据库，使用updateViews而不是incrementViews
                        // 这样可以一次性设置正确的值，避免并发问题
                        postsRepository.updateViews(articleId, newCount)
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe(
                                result -> {
                                    if (result > 0) {
                                        log.debug("数据库浏览量更新成功: ID={}, 新浏览量={}", articleId, newCount);
                                    } else {
                                        log.warn("数据库浏览量更新无影响: ID={}, 可能文章不存在", articleId);
                                    }
                                },
                                error -> log.error("同步浏览量到数据库失败: ID={}, 错误={}", articleId, error.getMessage())
                            );
                    }
                    
                    return newCount;
                }
            }
            
            // 缓存不可用，直接更新数据库
            // 但这种情况应该很少发生
            return postsRepository.incrementViews(articleId)
                .subscribeOn(Schedulers.boundedElastic())
                .block();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            log.error("增加文章浏览量失败: ID={}, 错误={}", articleId, e.getMessage());
            return Mono.just(0);
        });
    }

    @Override
    public Flux<ViewHistory> getUserViewHistory(Long userId) {
        return viewHistoryRepository.findByVisitorIdOrderByCreateTimeDesc(userId);
    }

    @Override
    public Mono<Map<String, Object>> getUserViewHistoryPaged(int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;

        final int finalPage = page;
        final int finalSize = size;

        return viewHistoryRepository.count()
                .flatMap(total -> {
                    long offset = (finalPage - 1) * finalSize;
                    return viewHistoryRepository.findOrderByCreateTimeDesc(finalSize, offset)
                            .flatMap(history ->
                                    postsRepository.findById(history.getPostId())
                                    .map(article -> viewHistoryMapper.toViewHistoryVO(history, article))
                                    .defaultIfEmpty(viewHistoryMapper.toViewHistoryVO(history))
                            )
                            .collectList()
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
    @Cacheable(cacheNames = CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME, key = "#articleId", unless = "#result == 0")
    public Mono<Long> getArticleViewCount(Long articleId) {
        log.debug("从数据库获取文章访问量: articleId={}", articleId);
        return viewHistoryRepository.countByPostId(articleId);
    }

    @Override
    public Mono<Map<Long, Long>> batchGetArticleViewCounts(Iterable<Long> articleIds) {
        return viewHistoryRepository.countByArticleIds(articleIds)
                .collectMap(
                        objects -> (Long) objects[0],  // articleId
                        objects -> (Long) objects[1]   // count
                )
                .defaultIfEmpty(Collections.emptyMap());
    }

    @Override
    @Transactional
    public Mono<Boolean> clearUserViewHistory(Long userId) {
        return viewHistoryRepository.findByVisitorIdOrderByCreateTimeDesc(userId)
                .flatMap(viewHistoryRepository::delete)
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
        org.springframework.cache.Cache postPvCacheObj = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME);
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
                        
                        // 获取总访问量
                        return getTotalViewsFromDatabase()
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
        
        // 获取总访问量
        return getTotalViewsFromDatabase()
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
        return postsRepository.findAll(Sort.by(Sort.Direction.DESC, "views"))
                .take(limit)
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
    @Cacheable(cacheNames = CacheConstants.VIEW_HISTORY_PV_CACHE_NAME, key = "'" + CacheConstants.STATS_TOTAL_KEY + "'", unless = "#result == 0")
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
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_PV_CACHE_NAME);
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
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_UV_CACHE_NAME);
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
     * 更新PV值
     */
    @CachePut(cacheNames = CacheConstants.VIEW_HISTORY_PV_CACHE_NAME, key = "#key")
    public Long updatePvValue(String key) {
        Long currentValue = getCachedPvValue(key);
        return currentValue + 1L;
    }
    
    /**
     * 更新文章PV值
     */
    @CachePut(cacheNames = CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME, key = "#key")
    public Long updatePostPvValue(String key) {
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME);
        if (cache != null) {
            ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                Object value = wrapper.get();
                if (value instanceof Long) {
                    return (Long) value + 1L;
                }
            }
        }
        return 1L;
    }
    
    /**
     * 更新UV集合
     */
    @CachePut(cacheNames = CacheConstants.VIEW_HISTORY_UV_CACHE_NAME, key = "#key")
    public Set<String> updateUvValue(String key, String visitorId) {
        Set<String> visitors = getCachedUvValue(key);
        visitors.add(visitorId);
        return visitors;
    }

    /**
     * 获取文章当前浏览量（优先从缓存获取，缓存无数据则从数据库获取）
     * 
     * @param articleId 文章ID
     * @return 浏览量
     */
    @Override
    public Mono<Integer> getArticleCurrentViews(Long articleId) {
        if (articleId == null) {
            return Mono.just(0);
        }
        
        return Mono.fromCallable(() -> {
            // 首先从缓存获取
            Cache viewCountCache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME);
            if (viewCountCache != null) {
                String cacheKey = ARTICLE_VIEW_COUNT_KEY + articleId;
                AtomicInteger counter = viewCountCache.get(cacheKey, AtomicInteger.class);
                if (counter != null) {
                    return counter.get();
                }
            }
            
            // 缓存中没有，从数据库获取
            return postsRepository.findById(articleId)
                .map(article -> article.getViews() != null ? article.getViews() : 0)
                .defaultIfEmpty(0)
                .block();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnSuccess(views -> log.debug("获取文章当前浏览量: ID={}, 浏览量={}", articleId, views))
        .onErrorResume(e -> {
            log.error("获取文章浏览量失败: ID={}, 错误={}", articleId, e.getMessage());
            return Mono.just(0);
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
            Cache viewCountCache = cacheManager.getCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE_NAME);
            if (viewCountCache == null) {
                return 0;
            }
            
            Object nativeCache = viewCountCache.getNativeCache();
            if (!(nativeCache instanceof com.github.benmanes.caffeine.cache.Cache)) {
                return 0;
            }
            
            @SuppressWarnings("unchecked")
            Map<Object, Object> cacheMap = ((com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache).asMap();
            
            AtomicInteger syncCount = new AtomicInteger(0);
            
            // 使用批处理方式更新，减少数据库连接次数
            List<Mono<Integer>> updateOperations = new ArrayList<>();
            
            cacheMap.forEach((key, value) -> {
                if (key instanceof String && value instanceof AtomicInteger) {
                    String cacheKey = (String) key;
                    if (cacheKey.startsWith(ARTICLE_VIEW_COUNT_KEY)) {
                        String articleIdStr = cacheKey.substring(ARTICLE_VIEW_COUNT_KEY.length());
                        try {
                            Long articleId = Long.parseLong(articleIdStr);
                            int viewCount = ((AtomicInteger) value).get();
                            
                            // 添加到批处理操作列表
                            updateOperations.add(
                                postsRepository.updateViews(articleId, viewCount)
                                    .doOnSuccess(result -> {
                                        if (result > 0) {
                                            syncCount.incrementAndGet();
                                            log.debug("同步文章浏览量成功: ID={}, 浏览量={}", articleId, viewCount);
                                        }
                                    })
                                    .onErrorResume(e -> {
                                        log.error("同步文章浏览量失败: ID={}, 错误={}", articleId, e.getMessage());
                                        return Mono.just(0);
                                    })
                            );
                        } catch (NumberFormatException e) {
                            log.warn("无效的文章ID格式: {}", articleIdStr);
                        }
                    }
                }
            });
            
            // 执行批处理操作
            if (!updateOperations.isEmpty()) {
                Flux.merge(updateOperations)
                    .collectList()
                    .subscribe(
                        results -> log.info("文章浏览量同步完成，成功同步 {} 篇文章", syncCount.get()),
                        error -> log.error("批量同步文章浏览量失败: {}", error.getMessage())
                    );
            }
            
            return syncCount.get();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnSuccess(count -> log.info("文章浏览量同步任务完成，共同步 {} 篇文章", count))
        .onErrorResume(e -> {
            log.error("同步文章浏览量到数据库失败: {}", e.getMessage());
            return Mono.just(0);
        });
    }
} 