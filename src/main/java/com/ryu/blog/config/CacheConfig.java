package com.ryu.blog.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ryu.blog.constant.CacheConstants;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类
 * 配置Spring Cache和Caffeine缓存管理器
 *
 * <p><b>整个应用只有这一个 {@link CacheManager} bean</b>。Spring 的缓存拦截器
 * （{@code CacheInterceptor}）按类型解析 {@code CacheManager}，多个 bean 时取
 * {@code @Primary} 的那个 —— 也就是说，额外再注册几个非 primary 的
 * {@code CacheManager} 并不会让 {@code @Cacheable(cacheNames = "xxx")} 走它们，
 * 只会让所有缓存（含这些名字）全部落进 primary 那个。
 * 曾经存在的 {@code shortTermCacheManager} / {@code longTermCacheManager} 就是
 * 这种「看起来分了级、其实完全没用上」的死配置，已删除。
 *
 * <p>要区分不同 TTL，请用 {@link #registerCustomCaches(CaffeineCacheManager)}
 * 为不同的<b>缓存名</b>注册各自的 Caffeine 配置。
 *
 * @author ryu 475118582@qq.com
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 默认缓存管理器
     * 适用于大多数场景，过期时间为30分钟
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        // 使用CaffeineCacheManager替代SimpleCacheManager以支持异步操作
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // 启用异步缓存模式
        cacheManager.setAsyncCacheMode(true);
        
        // 设置默认的缓存配置（用于未自定义配置的缓存）
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats());
        
        // 为特定缓存注册自定义配置
        // 注意：不需要先setCacheNames，registerCustomCache会自动注册
        registerCustomCaches(cacheManager);
        
        return cacheManager;
    }
    
    /**
     * 为CaffeineCacheManager注册自定义缓存配置
     * 所有缓存都使用异步模式以提高性能
     */
    private void registerCustomCaches(CaffeineCacheManager cacheManager) {
        // 所有缓存统一使用异步操作以提高并发性能
        
        // 系统配置缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.SYS_CONFIG_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(12, TimeUnit.HOURS)
                .maximumSize(200)
                .recordStats()
                .buildAsync());
        
        // 字典类型缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.DICT_TYPE_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(12, TimeUnit.HOURS)  // 字典类型变化很少，可以缓存12小时
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 字典项缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.DICT_ITEM_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(6, TimeUnit.HOURS)  // 字典项变化较少，可以缓存6小时
                .maximumSize(500)
                .recordStats()
                .buildAsync());
        
        // 分类缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.CATEGORY_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(4, TimeUnit.HOURS)  // 分类数据变化较少，可以缓存更长时间
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 标签缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.TAG_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.HOURS)  // 标签数据变化较少，可以缓存更长时间
                .maximumSize(300)
                .recordStats()
                .buildAsync());
        
        // 文章缓存 - 根据不同类型设置不同过期时间
        cacheManager.registerCustomCache(CacheConstants.POST_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)  // 默认30分钟
                .maximumSize(500)
                .recordStats()
                .buildAsync());
        
        // 热门文章缓存 - 较长时间缓存
        cacheManager.registerCustomCache(CacheConstants.POST_HOT_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)  // 热门文章变化较慢，可以缓存更长时间
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 文章详情缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.POST_DETAIL_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)  // 文章详情变化不频繁，可以缓存1小时
                .maximumSize(200)
                .recordStats()
                .buildAsync());
        
        // 前台文章列表缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.POST_FRONT_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)  // 前台列表需要较新数据
                .maximumSize(200)
                .recordStats()
                .buildAsync());
        
        // 后台文章列表缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.POST_ADMIN_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)  // 管理后台需要较新数据
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 评论缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.COMMENT_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(300)
                .recordStats()
                .buildAsync());
        
        // 用户缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.USER_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)  // 用户信息变化不频繁
                .maximumSize(300)
                .recordStats()
                .buildAsync());
        
        // 文件缓存
        // TTL 必须小于业务生成的预签名 URL 有效期（批量方法固定 3600 秒）：
        // 缓存值里带着 URL，缓存比 URL 活得久 = 返回已失效的链接。
        cacheManager.registerCustomCache(CacheConstants.FILE_CACHE,
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .buildAsync());
        
        // 统计缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.STATS_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)  // 统计数据需要较新
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 菜单缓存
        // TTL 从 12 小时降到 30 分钟：这个缓存是**进程内**的 Caffeine，
        // 多实例部署时改菜单只会驱逐当前实例的缓存，其它实例最长要等一个 TTL
        // 才能看到新菜单。12 小时太长，30 分钟是可接受的收敛窗口。
        // （要彻底解决需要换成 Redis 共享缓存。）
        cacheManager.registerCustomCache(CacheConstants.MENUS_CACHE,
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(50)
                .recordStats()
                .buildAsync());
        
        // 存储配置缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.STORAGE_CONFIG_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(6, TimeUnit.HOURS)  // 存储配置变化较少
                .maximumSize(50)
                .recordStats()
                .buildAsync());
        
        // 存储属性缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.STORAGE_PROPERTIES_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(6, TimeUnit.HOURS)
                .maximumSize(200)
                .recordStats()
                .buildAsync());
        
        // 存储访问URL缓存 - 中期缓存
        cacheManager.registerCustomCache(CacheConstants.STORAGE_ACCESS_URL_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // 存储客户端缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.STORAGE_CLIENT_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(6, TimeUnit.HOURS)
                .maximumSize(50)
                .recordStats()
                .buildAsync());
        
        // 分片上传缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.MULTIPART_UPLOAD_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .buildAsync());
        
        // 浏览历史PV缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.VIEW_HISTORY_PV_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
                .buildAsync());
        
        // 浏览历史UV缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.VIEW_HISTORY_UV_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
                .buildAsync());
        
        // 文章浏览量缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.VIEW_HISTORY_POST_PV_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(2000)
                .recordStats()
                .buildAsync());
        
        // 访问记录缓存 - 用于控制访问频率（修复：使用异步缓存）
        cacheManager.registerCustomCache(CacheConstants.VISIT_RECORD_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)  // 30分钟过期
                .maximumSize(10000)  // 支持更多并发用户
                .recordStats()
                .buildAsync());  // 修复：改为异步缓存
        
        // AI生成结果缓存 - 短期缓存（用于幂等性）
        cacheManager.registerCustomCache(CacheConstants.AI_GENERATION_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)  // 5分钟内相同请求返回缓存
                .maximumSize(100)
                .recordStats()
                .buildAsync());
        
        // AI模板缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.AI_TEMPLATE_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(6, TimeUnit.HOURS)  // 模板变化较少
                .maximumSize(50)
                .recordStats()
                .buildAsync());
        
        // AI配额缓存 - 短期缓存
        cacheManager.registerCustomCache(CacheConstants.AI_QUOTA_CACHE, 
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)  // 配额需要较新数据
                .maximumSize(500)
                .recordStats()
                .buildAsync());
        
        // AI提供商配置缓存 - 长期缓存
        cacheManager.registerCustomCache(CacheConstants.AI_PROVIDER_CONFIG_CACHE,
            Caffeine.newBuilder()
                .expireAfterWrite(12, TimeUnit.HOURS)  // 配置变化很少
                .maximumSize(20)
                .recordStats()
                .buildAsync());

        // 监控数据缓存 - 短期缓存。
        // 这三个缓存名此前没有注册，@Cacheable 会因为找不到同名缓存而静默失效。
        cacheManager.registerCustomCache(CacheConstants.MONITOR_SYSTEM_CACHE,
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .buildAsync());

        cacheManager.registerCustomCache(CacheConstants.MONITOR_THREADS_CACHE,
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .buildAsync());

        cacheManager.registerCustomCache(CacheConstants.MONITOR_THREAD_STATS_CACHE,
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .buildAsync());
    }
}