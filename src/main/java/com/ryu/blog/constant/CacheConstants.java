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
    
    /** 缓存名称 */
    public static final String POST_CACHE_NAME = "postCache";
    public static final String POST_HOT_CACHE_NAME = "postHotCache";
    public static final String POST_DETAIL_CACHE_NAME = "postDetailCache";
    public static final String POST_FRONT_CACHE_NAME = "postFrontCache";
    public static final String POST_ADMIN_CACHE_NAME = "postAdminCache";
    public static final String CATEGORY_CACHE_NAME = "categoryCache";
    public static final String TAG_CACHE_NAME = "tagCache";
    public static final String COMMENT_CACHE_NAME = "commentCache";
    public static final String USER_CACHE_NAME = "userCache";
    public static final String FILE_CACHE_NAME = "fileCache";
    public static final String STATS_CACHE_NAME = "statsCache";
    
    /** 用户相关缓存 */
    public static final String USER_CACHE_PREFIX = CACHE_PREFIX + "user:";
    public static final String USER_DETAIL_KEY = USER_CACHE_PREFIX + "detail:";
    public static final String USER_PERMISSIONS_KEY = USER_CACHE_PREFIX + "permissions:";
    public static final String USER_ROLES_KEY = USER_CACHE_PREFIX + "roles:";
    public static final String USER_INFO_KEY = USER_CACHE_PREFIX + "info:";
    public static final String USER_ID_KEY = USER_CACHE_PREFIX + "id:";
    public static final String USER_USERNAME_KEY = USER_CACHE_PREFIX + "username:";
    
    /** 文章相关缓存 */
    public static final String POST_CACHE_PREFIX = CACHE_PREFIX + "post:";
    public static final String POST_DETAIL_KEY = POST_CACHE_PREFIX + "detail:";
    public static final String POST_LIST_KEY = POST_CACHE_PREFIX + "list:";
    public static final String POST_COUNT_KEY = POST_CACHE_PREFIX + "count";
    public static final String POST_HOT_KEY = POST_CACHE_PREFIX + "hot:";
    public static final String POST_NEWEST_KEY = POST_CACHE_PREFIX + "newest";
    public static final String POST_RELATED_KEY = POST_CACHE_PREFIX + "related:";
    public static final String POST_FRONT_KEY = POST_CACHE_PREFIX + "front:";
    public static final String POST_ADMIN_KEY = POST_CACHE_PREFIX + "admin:page:";
    
    /** 标签相关缓存 */
    public static final String TAG_CACHE_PREFIX = CACHE_PREFIX + "tag:";
    public static final String TAG_ALL_KEY = TAG_CACHE_PREFIX + "all:";
    public static final String TAG_WITH_COUNT_KEY = TAG_CACHE_PREFIX + "all:count";
    public static final String TAG_HOT_KEY = TAG_CACHE_PREFIX + "hot:";
    public static final String TAG_ARTICLE_KEY = TAG_CACHE_PREFIX + "article:";
    public static final String TAG_DETAIL_KEY = TAG_CACHE_PREFIX + "detail:";
    public static final String TAG_PAGE_KEY = TAG_CACHE_PREFIX + "page:";
    public static final String TAG_PAGE_PATTERN = TAG_PAGE_KEY + "*";
    
    /** 分类相关缓存 */
    public static final String CATEGORY_CACHE_PREFIX = CACHE_PREFIX + "category:";
    public static final String CATEGORY_ALL_KEY = CATEGORY_CACHE_PREFIX + "all";
    public static final String CATEGORY_TREE_KEY = CATEGORY_CACHE_PREFIX + "tree";
    public static final String CATEGORY_DETAIL_KEY = CATEGORY_CACHE_PREFIX + "detail:";
    public static final String CATEGORY_STATS_KEY = CATEGORY_CACHE_PREFIX + "stats";
    public static final String CATEGORY_PAGE_KEY = CATEGORY_CACHE_PREFIX + "page:";
    public static final String CATEGORY_ARTICLE_KEY = CATEGORY_CACHE_PREFIX + "article:";
    public static final String CATEGORY_ARTICLE_IDS_KEY = CATEGORY_CACHE_PREFIX + "articleIds:";
    
    /** 评论相关缓存 */
    public static final String COMMENT_CACHE_PREFIX = CACHE_PREFIX + "comment:";
    public static final String COMMENT_POST_KEY = COMMENT_CACHE_PREFIX + "post:";
    public static final String COMMENT_COUNT_KEY = COMMENT_CACHE_PREFIX + "count:";
    public static final String COMMENT_ID_KEY = COMMENT_CACHE_PREFIX + "id:";
    public static final String COMMENT_ARTICLE_KEY = COMMENT_CACHE_PREFIX + "article:";
    public static final String COMMENT_ARTICLE_COUNT_KEY = COMMENT_CACHE_PREFIX + "article-count:";
    public static final String COMMENT_USER_KEY = COMMENT_CACHE_PREFIX + "user:";
    
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
    
    /** 资源组相关缓存 */
    public static final String RESOURCE_GROUP_CACHE_PREFIX = CACHE_PREFIX + "resource:group:";
    
    /** 防重复提交缓存键 */
    public static final String REPEAT_SUBMIT_KEY = CACHE_PREFIX + "repeat:submit:";
    
    /** 限流缓存键 */
    public static final String RATE_LIMIT_KEY = CACHE_PREFIX + "rate:limit:";
    
    /** 验证码缓存键 */
    public static final String CAPTCHA_CODE_KEY = CACHE_PREFIX + "captcha:";
    
    /** 登录用户缓存键 */
    public static final String LOGIN_TOKEN_KEY = CACHE_PREFIX + "login:token:";
    
    /** 系统配置缓存名称 */
    public static final String SYS_CONFIG_CACHE_NAME = "sysConfig";
    
    /** 系统配置缓存键 */
    public static final String CONFIG_VALUE_KEY = "value:";
    public static final String CONFIG_USER_VALUE_KEY = "userValue:";
    public static final String CONFIG_INFO_KEY = "config:";
    public static final String CONFIG_INFO_BY_ID_KEY = "configById:";
    public static final String CONFIG_ENTITY_KEY = "entity:";
    public static final String CONFIG_ENTITY_BY_ID_KEY = "entityById:";
    public static final String CONFIG_GROUPS_KEY = "configGroups";
    
    /** 存储相关缓存名称 */
    public static final String STORAGE_CONFIG_CACHE_NAME = "storageConfig";
    public static final String STORAGE_PROPERTIES_CACHE_NAME = "storageProperties";
    public static final String STORAGE_ACCESS_URL_CACHE_NAME = "accessUrl";
    
    /** 存储相关缓存键 */
    public static final String STORAGE_PROPERTIES_ALL_KEY = ":all";
    
    /** 浏览历史统计相关缓存名称 */
    public static final String VIEW_HISTORY_PV_CACHE_NAME = "viewHistoryPv";
    public static final String VIEW_HISTORY_UV_CACHE_NAME = "viewHistoryUv";
    public static final String VIEW_HISTORY_POST_PV_CACHE_NAME = "viewHistoryPostPv";
    public static final String VISIT_RECORD_CACHE_NAME = "visitRecordCache";
    
    /** 浏览历史统计相关缓存键前缀 */
    public static final String VIEW_HISTORY_PV_KEY_PREFIX = "pv:";
    public static final String VIEW_HISTORY_UV_KEY_PREFIX = "uv:";
    public static final String VIEW_HISTORY_POST_PV_KEY_PREFIX = "post:pv:";
    
    /** 存储策略客户端缓存名称 */
    public static final String STORAGE_CLIENT_CACHE_NAME = "storageClient";
    
    /** 存储策略客户端缓存键 */
    public static final String STORAGE_CLIENT_KEY = "client";
    public static final String STORAGE_BUCKET_KEY = "bucket";
    
    /** 分片上传缓存名称 */
    public static final String MULTIPART_UPLOAD_CACHE_NAME = "multipartUpload";
    
    /** 文件相关缓存键 */
    public static final String FILE_DETAIL_KEY = CACHE_PREFIX + "file:detail:";
    public static final String FILE_URL_KEY = CACHE_PREFIX + "file:url:";
    public static final String FILE_DOWNLOAD_KEY = CACHE_PREFIX + "file:download:";
} 