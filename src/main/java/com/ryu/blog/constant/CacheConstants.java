package com.ryu.blog.constant;

/**
 * 缓存相关常量
 *
 * 命名规范：
 * - 缓存名称常量：xxx_CACHE（如 POST_CACHE = "postCache"）
 * - 缓存键前缀常量：xxx_KEY_PREFIX（如 POST_KEY_PREFIX = "post:"）
 * - 完整缓存键常量：xxx_KEY（只放前缀，不带引号；用法是 key = "'" + XXX_KEY + "' + #参数"）
 *
 * @author ryu
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    /** 缓存前缀 */
    public static final String CACHE_PREFIX = "blog:";

    /** 过期时间（秒） */
    public static final long DEFAULT_EXPIRE = 3600;
    public static final long LONG_EXPIRE = 86400;
    public static final long SHORT_EXPIRE = 300;

    // ==================== 缓存名称常量 ====================
    // 用于 @Cacheable(cacheNames = "xxx") 或 @CacheConfig(cacheNames = "xxx")

    /** 文章相关缓存 */
    public static final String POST_CACHE = "postCache";
    public static final String POST_HOT_CACHE = "postHotCache";
    public static final String POST_DETAIL_CACHE = "postDetailCache";
    public static final String POST_FRONT_CACHE = "postFrontCache";
    public static final String POST_ADMIN_CACHE = "postAdminCache";

    /** 分类相关缓存 */
    public static final String CATEGORY_CACHE = "categoryCache";

    /** 标签相关缓存 */
    public static final String TAG_CACHE = "tagCache";

    /** 评论相关缓存 */
    public static final String COMMENT_CACHE = "commentCache";

    /** 用户相关缓存 */
    public static final String USER_CACHE = "userCache";

    /** 文件相关缓存 */
    public static final String FILE_CACHE = "fileCache";

    /** 统计相关缓存 */
    public static final String STATS_CACHE = "statsCache";

    /** 菜单相关缓存 */
    public static final String MENUS_CACHE = "menus";

    /** 系统配置缓存 */
    public static final String SYS_CONFIG_CACHE = "sysConfig";

    /** 字典类型缓存 */
    public static final String DICT_TYPE_CACHE = "dictType";

    /** 字典项缓存 */
    public static final String DICT_ITEM_CACHE = "dictItem";

    /** 存储配置缓存 */
    public static final String STORAGE_CONFIG_CACHE = "storageConfig";
    public static final String STORAGE_PROPERTIES_CACHE = "storageProperties";
    public static final String STORAGE_ACCESS_URL_CACHE = "accessUrl";
    public static final String STORAGE_CLIENT_CACHE = "storageClient";

    /** 分片上传缓存 */
    public static final String MULTIPART_UPLOAD_CACHE = "multipartUpload";

    /** 浏览历史统计缓存 */
    public static final String VIEW_HISTORY_PV_CACHE = "viewHistoryPv";
    public static final String VIEW_HISTORY_UV_CACHE = "viewHistoryUv";
    public static final String VIEW_HISTORY_POST_PV_CACHE = "viewHistoryPostPv";
    public static final String VISIT_RECORD_CACHE = "visitRecordCache";

    /** AI相关缓存 */
    public static final String AI_GENERATION_CACHE = "aiGeneration";
    public static final String AI_TEMPLATE_CACHE = "aiTemplate";
    public static final String AI_QUOTA_CACHE = "aiQuota";
    public static final String AI_PROVIDER_CONFIG_CACHE = "aiProviderConfig";

    /** 监控相关缓存（短 TTL，避免频繁采集系统指标） */
    public static final String MONITOR_SYSTEM_CACHE = "monitor:system";
    public static final String MONITOR_THREADS_CACHE = "monitor:threads";
    public static final String MONITOR_THREAD_STATS_CACHE = "monitor:thread-stats";

    // ==================== 缓存键前缀常量 ====================
    // 用于拼接缓存键，如：CACHE_PREFIX + USER_KEY_PREFIX + "detail:" + #id

    /** 用户相关缓存键前缀 */
    public static final String USER_KEY_PREFIX = "user:";
    public static final String USER_DETAIL_KEY_PREFIX = USER_KEY_PREFIX + "detail:";
    public static final String USER_INFO_KEY_PREFIX = USER_KEY_PREFIX + "info:";
    public static final String USER_ID_KEY_PREFIX = USER_KEY_PREFIX + "id:";
    public static final String USER_USERNAME_KEY_PREFIX = USER_KEY_PREFIX + "username:";

    /** 用户缓存完整键（用于SpEL表达式拼接） */
    public static final String USER_ID_KEY = USER_KEY_PREFIX + "id:";
    public static final String USER_USERNAME_KEY = USER_KEY_PREFIX + "username:";
    public static final String USER_INFO_KEY = USER_KEY_PREFIX + "info:";

    /** 文章相关缓存键前缀 */
    public static final String POST_KEY_PREFIX = "post:";
    public static final String POST_DETAIL_KEY_PREFIX = POST_KEY_PREFIX + "detail:";
    public static final String POST_LIST_KEY_PREFIX = POST_KEY_PREFIX + "list:";
    public static final String POST_COUNT_KEY_PREFIX = POST_KEY_PREFIX + "count";
    public static final String POST_HOT_KEY_PREFIX = POST_KEY_PREFIX + "hot:";
    public static final String POST_NEWEST_KEY_PREFIX = POST_KEY_PREFIX + "newest";
    public static final String POST_RELATED_KEY_PREFIX = POST_KEY_PREFIX + "related:";
    public static final String POST_FRONT_KEY_PREFIX = POST_KEY_PREFIX + "front:";
    public static final String POST_ADMIN_KEY_PREFIX = POST_KEY_PREFIX + "admin:page:";

    /** 标签相关缓存键前缀 */
    public static final String TAG_KEY_PREFIX = "tag:";
    public static final String TAG_ALL_KEY_PREFIX = TAG_KEY_PREFIX + "all:";
    public static final String TAG_WITH_COUNT_KEY_PREFIX = TAG_KEY_PREFIX + "all:count";
    public static final String TAG_HOT_KEY_PREFIX = TAG_KEY_PREFIX + "hot:";
    public static final String TAG_ARTICLE_KEY_PREFIX = TAG_KEY_PREFIX + "article:";
    public static final String TAG_DETAIL_KEY_PREFIX = TAG_KEY_PREFIX + "detail:";
    public static final String TAG_PAGE_KEY_PREFIX = TAG_KEY_PREFIX + "page:";

    /** 标签缓存完整键（用于SpEL表达式拼接） */
    public static final String TAG_ARTICLE_KEY = TAG_KEY_PREFIX + "article:";
    public static final String TAG_HOT_KEY = TAG_KEY_PREFIX + "hot:";

    /** 分类相关缓存键前缀 */
    public static final String CATEGORY_KEY_PREFIX = "category:";
    public static final String CATEGORY_ALL_KEY_PREFIX = CATEGORY_KEY_PREFIX + "all";
    public static final String CATEGORY_TREE_KEY_PREFIX = CATEGORY_KEY_PREFIX + "tree";
    public static final String CATEGORY_DETAIL_KEY_PREFIX = CATEGORY_KEY_PREFIX + "detail:";
    public static final String CATEGORY_STATS_KEY_PREFIX = CATEGORY_KEY_PREFIX + "stats";
    public static final String CATEGORY_PAGE_KEY_PREFIX = CATEGORY_KEY_PREFIX + "page:";
    public static final String CATEGORY_ARTICLE_KEY_PREFIX = CATEGORY_KEY_PREFIX + "article:";
    public static final String CATEGORY_ARTICLE_IDS_KEY_PREFIX = CATEGORY_KEY_PREFIX + "articleIds:";

    /** 分类缓存完整键（用于SpEL表达式拼接） */
    public static final String CATEGORY_PAGE_KEY = CATEGORY_KEY_PREFIX + "page:";
    public static final String CATEGORY_ARTICLE_KEY = CATEGORY_KEY_PREFIX + "article:";
    public static final String CATEGORY_ARTICLE_IDS_KEY = CATEGORY_KEY_PREFIX + "articleIds:";

    /** 评论相关缓存键前缀 */
    public static final String COMMENT_KEY_PREFIX = "comment:";
    public static final String COMMENT_POST_KEY_PREFIX = COMMENT_KEY_PREFIX + "post:";
    public static final String COMMENT_COUNT_KEY_PREFIX = COMMENT_KEY_PREFIX + "count:";
    public static final String COMMENT_ID_KEY_PREFIX = COMMENT_KEY_PREFIX + "id:";
    public static final String COMMENT_ARTICLE_KEY_PREFIX = COMMENT_KEY_PREFIX + "article:";
    public static final String COMMENT_ARTICLE_COUNT_KEY_PREFIX = COMMENT_KEY_PREFIX + "article-count:";
    public static final String COMMENT_USER_KEY_PREFIX = COMMENT_KEY_PREFIX + "user:";

    /** 评论缓存完整键（用于SpEL表达式拼接） */
    public static final String COMMENT_ARTICLE_KEY = COMMENT_KEY_PREFIX + "article:";
    public static final String COMMENT_ID_KEY = COMMENT_KEY_PREFIX + "id:";
    public static final String COMMENT_USER_KEY = COMMENT_KEY_PREFIX + "user:";

    /** 浏览量相关缓存键前缀 */
    public static final String VIEW_KEY_PREFIX = "view:";
    public static final String VIEW_COUNT_KEY_PREFIX = VIEW_KEY_PREFIX + "count:";

    /** 浏览量缓存完整键（用于SpEL表达式拼接） */
    public static final String VIEW_CACHE_PREFIX = "view:";
    public static final String VIEW_COUNT_KEY = "view:count:";

    /** 统计相关缓存键前缀 */
    public static final String STATS_KEY_PREFIX = "stats:";
    public static final String STATS_DAILY_KEY_PREFIX = STATS_KEY_PREFIX + "daily:";
    public static final String STATS_TOTAL_KEY_PREFIX = STATS_KEY_PREFIX + "total";

    /** 统计缓存完整键（用于SpEL表达式拼接） */
    public static final String STATS_TOTAL_KEY = "stats:total";

    /** 菜单相关缓存键前缀 */
    public static final String MENUS_TREE_KEY_PREFIX = "tree";
    public static final String MENUS_ALL_KEY_PREFIX = "all";

    /** 系统配置相关缓存键前缀 */
    public static final String CONFIG_KEY_PREFIX = "config:";
    public static final String CONFIG_ALL_KEY_PREFIX = CONFIG_KEY_PREFIX + "all";
    public static final String CONFIG_BY_KEY_PREFIX = CONFIG_KEY_PREFIX + "key:";

    /** 资源组相关缓存键前缀 */
    public static final String RESOURCE_GROUP_KEY_PREFIX = "resource:group:";
    public static final String RESOURCE_GROUP_CACHE_PREFIX = "resource:group:";

    /** 防重复提交缓存键前缀 */
    public static final String REPEAT_SUBMIT_KEY_PREFIX = "repeat:submit:";

    /** 限流缓存键前缀 */
    public static final String RATE_LIMIT_KEY_PREFIX = "rate:limit:";

    /** 验证码缓存键前缀 */
    public static final String CAPTCHA_CODE_KEY_PREFIX = "captcha:";

    /** 登录用户缓存键前缀 */
    public static final String LOGIN_TOKEN_KEY_PREFIX = "login:token:";

    /** 系统配置缓存键前缀 */
    public static final String CONFIG_VALUE_KEY_PREFIX = "value:";
    public static final String CONFIG_USER_VALUE_KEY_PREFIX = "userValue:";
    public static final String CONFIG_INFO_KEY_PREFIX = "config:";
    public static final String CONFIG_INFO_BY_ID_KEY_PREFIX = "configById:";
    public static final String CONFIG_ENTITY_KEY_PREFIX = "entity:";
    public static final String CONFIG_ENTITY_BY_ID_KEY_PREFIX = "entityById:";
    public static final String CONFIG_GROUPS_KEY_PREFIX = "configGroups";

    /** 字典类型缓存键前缀 */
    public static final String DICT_TYPE_ALL_KEY_PREFIX = "all";
    public static final String DICT_TYPE_BY_ID_KEY_PREFIX = "id:";
    public static final String DICT_TYPE_BY_CODE_KEY_PREFIX = "code:";

    /** 字典项缓存键前缀 */
    public static final String DICT_ITEM_BY_ID_KEY_PREFIX = "id:";
    public static final String DICT_ITEM_BY_TYPE_KEY_PREFIX = "type:";
    public static final String DICT_ITEM_BY_TYPE_CODE_KEY_PREFIX = "typeCode:";
    public static final String DICT_ITEM_VALUE_KEY_PREFIX = "value:";

    /** 字典项缓存完整键（用于SpEL表达式拼接） */
    public static final String DICT_ITEM_BY_ID_KEY = "id:";
    public static final String DICT_ITEM_BY_TYPE_KEY = "type:";
    public static final String DICT_ITEM_BY_TYPE_CODE_KEY = "typeCode:";
    public static final String DICT_ITEM_VALUE_KEY = "value:";

    /** 存储相关缓存键前缀 */
    public static final String STORAGE_PROPERTIES_ALL_KEY_PREFIX = ":all";

    /** 浏览历史统计相关缓存键前缀 */
    public static final String VIEW_HISTORY_PV_KEY_PREFIX = "pv:";
    public static final String VIEW_HISTORY_UV_KEY_PREFIX = "uv:";
    public static final String VIEW_HISTORY_POST_PV_KEY_PREFIX = "post:pv:";

    /** 存储策略客户端缓存键前缀 */
    public static final String STORAGE_CLIENT_KEY_PREFIX = "client";
    public static final String STORAGE_BUCKET_KEY_PREFIX = "bucket";

    /** 存储策略客户端缓存完整键（用于SpEL表达式拼接） */
    public static final String STORAGE_CLIENT_KEY = "client";
    public static final String STORAGE_BUCKET_KEY = "bucket";

    /**
     * 文件相关缓存键前缀
     *
     * <p>注意：这里的值必须与实际使用的缓存键一致。
     * 原来写的是 "file:detail:" / "file:url:" / "file:download:"，
     * 而代码里真正用的键是 "info:" / "url:" / "download:"（见 FILE_*_KEY），
     * 两套对不上，等于这些常量从来没生效过。
     */
    public static final String FILE_INFO_KEY_PREFIX = "info:";
    public static final String FILE_URL_KEY_PREFIX = "url:";
    public static final String FILE_DOWNLOAD_KEY_PREFIX = "download:";
    public static final String FILE_VERSIONS_KEY_PREFIX = "versions:";
    public static final String FILE_USER_KEY_PREFIX = "user:";
    public static final String FILE_TYPE_KEY_PREFIX = "type:";
    public static final String FILE_INFOS_KEY_PREFIX = "infos:";
    public static final String FILE_PERMANENT_URLS_KEY_PREFIX = "permanent-urls:";
    public static final String FILE_URLS_KEY_PREFIX = "urls:";

    /** AI相关缓存键前缀 */
    public static final String AI_GENERATION_KEY_PREFIX = "generation:";
    public static final String AI_TEMPLATE_KEY_PREFIX = "template:";
    public static final String AI_QUOTA_KEY_PREFIX = "quota:user:";
    public static final String AI_PROVIDER_CONFIG_KEY_PREFIX = "provider:";
    public static final String AI_RATE_LIMIT_KEY_PREFIX = "ai:ratelimit:";

    // ==================== 完整缓存键常量（SpEL表达式） ====================
    // 用于 @Cacheable(key = "...") 或 @CacheEvict(key = "...")

    /** 文章缓存完整键 */
    public static final String POST_DETAIL_KEY = POST_KEY_PREFIX + "detail:";
    public static final String POST_HOT_KEY = POST_KEY_PREFIX + "hot:";
    public static final String POST_RELATED_KEY = POST_KEY_PREFIX + "related:";
    public static final String POST_FRONT_KEY = POST_KEY_PREFIX + "front:";
    public static final String POST_PAGE_KEY = POST_KEY_PREFIX + "page:";

    /** 用户缓存完整键 */
    public static final String USER_DETAIL_KEY = USER_KEY_PREFIX + "detail:";

    /** 标签缓存完整键 */
    public static final String TAG_ALL_KEY = TAG_KEY_PREFIX + "all:";
    public static final String TAG_DETAIL_KEY = TAG_KEY_PREFIX + "detail:";
    public static final String TAG_PAGE_KEY = TAG_KEY_PREFIX + "page:";

    /** 分类缓存完整键 */
    public static final String CATEGORY_ALL_KEY = CATEGORY_KEY_PREFIX + "all";
    public static final String CATEGORY_TREE_KEY = CATEGORY_KEY_PREFIX + "tree";
    public static final String CATEGORY_DETAIL_KEY = CATEGORY_KEY_PREFIX + "detail:";

    /** 评论缓存完整键 */
    public static final String COMMENT_COUNT_KEY = COMMENT_KEY_PREFIX + "count:";
    public static final String COMMENT_ARTICLE_COUNT_KEY = COMMENT_KEY_PREFIX + "article-count:";

    /** 字典缓存完整键 */
    public static final String DICT_TYPE_ALL_KEY = "all";
    public static final String DICT_TYPE_BY_ID_KEY = "id:";
    public static final String DICT_TYPE_BY_CODE_KEY = "code:";

    /** 菜单缓存完整键 */
    public static final String MENUS_TREE_KEY = "tree";
    public static final String MENUS_ALL_KEY = "all";

    /**
     * 系统配置缓存完整键（SpEL 用）
     *
     * <p><b>这里只放前缀，不放引号，也不放 SpEL 片段</b>。
     * 用法统一是 {@code key = "'" + CacheConstants.XXX_KEY + "' + #参数"}，
     * 结果形如 {@code 'entity:' + #key}。
     *
     * <p>原先这几个常量写成了 {@code "'entity:' + #key"}（自带引号和 SpEL），
     * 而调用处又套了一层引号 → 最终 SpEL 是 {@code ''entity:' + #key' + #key}，
     * <b>语法错误，每次读配置都会抛 SpelParseException</b> —— 配置服务（含 AI Provider
     * 读取自己的配置）整个不可用。
     */
    public static final String CONFIG_VALUE_KEY = CONFIG_VALUE_KEY_PREFIX;
    public static final String CONFIG_USER_VALUE_KEY = CONFIG_USER_VALUE_KEY_PREFIX;
    public static final String CONFIG_INFO_KEY = CONFIG_INFO_KEY_PREFIX;
    public static final String CONFIG_INFO_BY_ID_KEY = CONFIG_INFO_BY_ID_KEY_PREFIX;
    public static final String CONFIG_ENTITY_KEY = CONFIG_ENTITY_KEY_PREFIX;
    public static final String CONFIG_ENTITY_BY_ID_KEY = CONFIG_ENTITY_BY_ID_KEY_PREFIX;
    public static final String CONFIG_GROUPS_KEY = CONFIG_GROUPS_KEY_PREFIX;

    /**
     * 文件缓存完整键（SpEL 用，同样只放前缀）
     */
    public static final String FILE_INFO_KEY = FILE_INFO_KEY_PREFIX;
    public static final String FILE_URL_KEY = FILE_URL_KEY_PREFIX;
    public static final String FILE_DOWNLOAD_KEY = FILE_DOWNLOAD_KEY_PREFIX;
    public static final String FILE_VERSIONS_KEY = FILE_VERSIONS_KEY_PREFIX;
    public static final String FILE_USER_KEY = FILE_USER_KEY_PREFIX;
    public static final String FILE_TYPE_KEY = FILE_TYPE_KEY_PREFIX;
    public static final String FILE_INFOS_KEY = FILE_INFOS_KEY_PREFIX;
    public static final String FILE_PERMANENT_URLS_KEY = FILE_PERMANENT_URLS_KEY_PREFIX;
    public static final String FILE_URLS_KEY = FILE_URLS_KEY_PREFIX;
}
