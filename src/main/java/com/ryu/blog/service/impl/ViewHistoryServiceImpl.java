package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.entity.ViewHistory;
import com.ryu.blog.mapper.ViewHistoryMapper;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.repository.ViewHistoryRepository;
import com.ryu.blog.service.ViewHistoryService;
import com.ryu.blog.vo.ViewHistoryStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    @Override
    @Transactional
    public Mono<Boolean> addViewHistory(Long articleId, Long userId) {
        // 先检查是否已经有浏览记录
        return viewHistoryRepository.findByVisitorIdAndPostId(userId, articleId)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        // 如果已经有记录，则更新时间
                        return viewHistoryRepository.findByVisitorIdAndPostId(userId, articleId)
                                .flatMap(history -> {
                                    history.setUpdateTime(LocalDateTime.now());
                                    return viewHistoryRepository.save(history)
                                            .flatMap(savedHistory -> updateArticleViewCount(articleId))
                                            .thenReturn(true);
                                });
                    } else {
                        // 如果没有记录，则添加新记录
                        ViewHistory history = new ViewHistory();
                        history.setPostId(articleId);
                        history.setVisitorId(userId.toString());
                        history.setViewTime(LocalDateTime.now());
                        history.setCreateTime(LocalDateTime.now());
                        history.setUpdateTime(LocalDateTime.now());
                        
                        // 更新缓存统计数据
                        updateCacheStats(articleId, userId.toString());
                        
                        return viewHistoryRepository.save(history)
                                .flatMap(savedHistory -> updateArticleViewCount(articleId))
                                .thenReturn(true);
                    }
                });
    }

    /**
     * 更新文章浏览量
     */
    private Mono<Posts> updateArticleViewCount(Long articleId) {
        return postsRepository.findById(articleId)
                .flatMap(article -> {
                    Integer views = article.getViews() == null ? 0 : article.getViews();
                    article.setViews(views + 1);
                    return postsRepository.save(article);
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
} 