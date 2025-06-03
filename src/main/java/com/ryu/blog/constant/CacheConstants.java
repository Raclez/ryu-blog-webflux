package com.ryu.blog.constant;

/**
 * 缓存相关常量
 * 
 * @author ryu
 */
public class CacheConstants {
    
    /** 缓存前缀 */
    public static final String CACHE_PREFIX = "blog:";
    
    /** 过期时间 */
    public static final long DEFAULT_EXPIRE = 3600; // 默认过期时间（秒）
    public static final long LONG_EXPIRE = 86400; // 一天
    public static final long SHORT_EXPIRE = 300; // 5分钟
    
    /** 用户相关缓存 */
    public static final String USER_CACHE_PREFIX = CACHE_PREFIX + "user:";
    public static final String USER_DETAIL_KEY = USER_CACHE_PREFIX + "detail:";
    public static final String USER_PERMISSIONS_KEY = USER_CACHE_PREFIX + "permissions:";
    public static final String USER_ROLES_KEY = USER_CACHE_PREFIX + "roles:";
    
    /** 文章相关缓存 */
    public static final String POST_CACHE_PREFIX = CACHE_PREFIX + "post:";
    public static final String POST_DETAIL_KEY = POST_CACHE_PREFIX + "detail:";
    public static final String POST_LIST_KEY = POST_CACHE_PREFIX + "list:";
    public static final String POST_COUNT_KEY = POST_CACHE_PREFIX + "count";
    public static final String POST_HOT_KEY = POST_CACHE_PREFIX + "hot";
    public static final String POST_NEWEST_KEY = POST_CACHE_PREFIX + "newest";
    
    /** 标签相关缓存 */
    public static final String TAG_CACHE_PREFIX = CACHE_PREFIX + "tag:";
    public static final String TAG_ALL_KEY = TAG_CACHE_PREFIX + "all";
    public static final String TAG_WITH_COUNT_KEY = TAG_CACHE_PREFIX + "all:count";
    public static final String TAG_HOT_KEY = TAG_CACHE_PREFIX + "hot:";
    public static final String ARTICLE_TAGS_KEY = TAG_CACHE_PREFIX + "article:";
    
    /** 分类相关缓存 */
    public static final String CATEGORY_CACHE_PREFIX = CACHE_PREFIX + "category:";
    public static final String CATEGORY_ALL_KEY = CATEGORY_CACHE_PREFIX + "all";
    public static final String CATEGORY_TREE_KEY = CATEGORY_CACHE_PREFIX + "tree";
    
    /** 评论相关缓存 */
    public static final String COMMENT_CACHE_PREFIX = CACHE_PREFIX + "comment:";
    public static final String COMMENT_POST_KEY = COMMENT_CACHE_PREFIX + "post:";
    public static final String COMMENT_COUNT_KEY = COMMENT_CACHE_PREFIX + "count:";
    
    /** 浏览量相关缓存 */
    public static final String VIEW_CACHE_PREFIX = CACHE_PREFIX + "view:";
    public static final String VIEW_COUNT_KEY = VIEW_CACHE_PREFIX + "count:";
    
    /** 统计相关缓存 */
    public static final String STATS_CACHE_PREFIX = CACHE_PREFIX + "stats:";
    public static final String STATS_DAILY_KEY = STATS_CACHE_PREFIX + "daily:";
    public static final String STATS_TOTAL_KEY = STATS_CACHE_PREFIX + "total";
    
    /** 系统配置相关缓存 */
    public static final String CONFIG_CACHE_PREFIX = CACHE_PREFIX + "config:";
    public static final String CONFIG_ALL_KEY = CONFIG_CACHE_PREFIX + "all";
    public static final String CONFIG_BY_KEY = CONFIG_CACHE_PREFIX + "key:";
    
    /** 防重复提交缓存键 */
    public static final String REPEAT_SUBMIT_KEY = CACHE_PREFIX + "repeat:submit:";
    
    /** 限流缓存键 */
    public static final String RATE_LIMIT_KEY = CACHE_PREFIX + "rate:limit:";
    
    /** 验证码缓存键 */
    public static final String CAPTCHA_CODE_KEY = CACHE_PREFIX + "captcha:";
    
    /** 登录用户缓存键 */
    public static final String LOGIN_TOKEN_KEY = CACHE_PREFIX + "login:token:";
} 