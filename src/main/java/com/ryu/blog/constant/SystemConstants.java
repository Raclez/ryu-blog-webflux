package com.ryu.blog.constant;

/**
 * 系统常量
 * 
 * @author ryu
 */
public class SystemConstants {
    
    /** 系统相关 */
    public static final String SYSTEM_NAME = "Ryu Blog";
    public static final String SYSTEM_VERSION = "1.0.0";
    public static final String SYSTEM_AUTHOR = "ryu";
    public static final String SYSTEM_EMAIL = "475118582@qq.com";
    
    /** 通用状态 */
    public static final int STATUS_NORMAL = 1;   // 正常
    public static final int STATUS_DISABLED = 0; // 禁用
    
    /** 删除标记 */
    public static final int NOT_DELETED = 0;     // 未删除
    public static final int IS_DELETED = 1;      // 已删除
    
    /** 性别 */
    public static final int GENDER_MALE = 1;     // 男
    public static final int GENDER_FEMALE = 2;   // 女
    public static final int GENDER_UNKNOWN = 0;  // 未知
    
    /** 是否 */
    public static final int YES = 1;
    public static final int NO = 0;
    
    /** 文章状态 */
    public static final int POST_STATUS_DRAFT = 0;      // 草稿
    public static final int POST_STATUS_PUBLISHED = 1;  // 已发布
    public static final int POST_STATUS_PENDING = 2;    // 待审核
    public static final int POST_STATUS_REJECTED = 3;   // 已拒绝
    
    /** 评论状态 */
    public static final int COMMENT_STATUS_PENDING = 0; // 待审核
    public static final int COMMENT_STATUS_APPROVED = 1; // 已通过
    public static final int COMMENT_STATUS_REJECTED = 2; // 已拒绝
    
    /** 文件相关 */
    public static final String DEFAULT_AVATAR = "/static/images/avatar/default.png";
    public static final String DEFAULT_COVER = "/static/images/cover/default.jpg";
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
    /** 分页默认参数 */
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_PAGE_NUMBER = 1;
    public static final int MAX_PAGE_SIZE = 100;
    
    /** 时间格式 */
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm:ss";
    
    /** 响应相关 */
    public static final String RESPONSE_RESULT_ANN = "RESPONSE_RESULT_ANN";
    
    /** 安全相关 */
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int PASSWORD_MAX_LENGTH = 20;
    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 20;
    
    /** 数据权限 */
    public static final String DATA_SCOPE_ALL = "1";      // 全部数据权限
    public static final String DATA_SCOPE_CUSTOM = "2";   // 自定义数据权限
    public static final String DATA_SCOPE_DEPT = "3";     // 本部门数据权限
    public static final String DATA_SCOPE_DEPT_AND_CHILD = "4"; // 本部门及以下数据权限
    public static final String DATA_SCOPE_SELF = "5";     // 仅本人数据权限
    
    /** 限流相关 */
    public static final int DEFAULT_LIMIT_REQUEST = 100;  // 默认限制请求数
    public static final int DEFAULT_LIMIT_TIMEOUT = 60;   // 默认限制时间窗口（秒）
    
    /** Token相关 */
    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final int TOKEN_EXPIRE_TIME = 86400;    // 过期时间（秒）= 1天
    public static final int REFRESH_TOKEN_EXPIRE_TIME = 604800; // 刷新token过期时间（秒）= 7天
    
    /** 防重复提交 */
    public static final int REPEAT_SUBMIT_INTERVAL = 5;   // 防重复提交间隔（秒）
    
    /** 超级管理员 */
    public static final String SUPER_ADMIN = "admin";
    public static final Long SUPER_ADMIN_ID = 1L;
    public static final String SUPER_ADMIN_ROLE = "超级管理员";
    public static final String SUPER_ADMIN_ROLE_KEY = "admin";
} 