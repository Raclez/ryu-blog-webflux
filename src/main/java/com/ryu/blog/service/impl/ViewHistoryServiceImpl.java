package com.ryu.blog.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.concurrent.TimeUnit;

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

    // 缓存键前缀常量
    private static final String PV_KEY_PREFIX = "stats:pv:";
    private static final String UV_KEY_PREFIX = "stats:uv:";
    private static final String POST_PV_KEY_PREFIX = "stats:post:pv:";
    private static final String LOCATION_STATS_KEY = "stats:location";
    private static final String DEVICE_STATS_KEY = "stats:device";

    // Caffeine缓存
    private final Cache<String, Long> pvCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .maximumSize(100)
            .build();
            
    private final Cache<String, Set<String>> uvCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .maximumSize(100)
            .build();
            
    private final Cache<String, Long> postPvCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .maximumSize(1000)
            .build();
    
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
    public Mono<Long> getArticleViewCount(Long articleId) {
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
        Long todayPv = pvCache.getIfPresent(PV_KEY_PREFIX + today);
        Long yesterdayPv = pvCache.getIfPresent(PV_KEY_PREFIX + yesterday);
        
        statsVO.setTodayViews(todayPv != null ? todayPv : 0L);
        statsVO.setYesterdayViews(yesterdayPv != null ? yesterdayPv : 0L);
        
        // 2. 获取每日趋势数据
        Map<String, Long> dailyViewsTrend = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).format(formatter);
            Long datePv = pvCache.getIfPresent(PV_KEY_PREFIX + date);
            dailyViewsTrend.put(date, datePv != null ? datePv : 0L);
        }
        statsVO.setDailyViewsTrend(dailyViewsTrend);
        
        // 3. 计算本周和本月PV
        long weeklyPv = dailyViewsTrend.values().stream().mapToLong(Long::longValue).sum();
        statsVO.setWeeklyViews(weeklyPv);
        
        long monthlyPv = 0L;
        for (int i = 0; i < 30; i++) {
            String date = LocalDate.now().minusDays(i).format(formatter);
            Long datePv = pvCache.getIfPresent(PV_KEY_PREFIX + date);
            if (datePv != null) {
                monthlyPv += datePv;
            }
        }
        statsVO.setMonthlyViews(monthlyPv);
        
        // 4. 获取今日UV
        Set<String> uniqueVisitors = uvCache.getIfPresent(UV_KEY_PREFIX + today);
        statsVO.setUniqueVisitors(uniqueVisitors != null ? (long) uniqueVisitors.size() : 0L);
        
        // 5. 获取访问量最高的文章
        Map<Long, Integer> topPosts = new LinkedHashMap<>();
        
        // 从缓存中获取文章访问量
        postPvCache.asMap().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    String key = entry.getKey();
                    if (key.startsWith(POST_PV_KEY_PREFIX)) {
                        String postIdStr = key.substring(POST_PV_KEY_PREFIX.length());
                        try {
                            Long postId = Long.parseLong(postIdStr);
                            topPosts.put(postId, entry.getValue().intValue());
                        } catch (NumberFormatException e) {
                            log.warn("无法解析文章ID: {}", postIdStr);
                        }
                    }
                });
        
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
    private Mono<Long> getTotalViewsFromDatabase() {
        return viewHistoryRepository.count();
    }
    
    /**
     * 更新缓存中的统计数据
     */
    private void updateCacheStats(Long articleId, String visitorId) {
        // 获取当前日期
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // 更新PV
        String pvKey = PV_KEY_PREFIX + today;
        pvCache.asMap().compute(pvKey, (k, v) -> v == null ? 1L : v + 1L);
        
        // 更新文章PV
        String postPvKey = POST_PV_KEY_PREFIX + articleId;
        postPvCache.asMap().compute(postPvKey, (k, v) -> v == null ? 1L : v + 1L);
        
        // 更新UV
        String uvKey = UV_KEY_PREFIX + today;
        Set<String> visitors = uvCache.get(uvKey, k -> new HashSet<>());
        visitors.add(visitorId);
        uvCache.put(uvKey, visitors);
    }
} 