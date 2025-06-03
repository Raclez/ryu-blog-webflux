package com.ryu.blog.constant;

/**
 * 错误码常量
 * 
 * @author ryu
 */
public class ErrorCodeConstants {
    
    /** 成功 */
    public static final int SUCCESS = 200;
    
    /** 系统级错误码 */
    public static final int ERROR = 500;                   // 系统内部错误
    public static final int BAD_REQUEST = 400;             // 错误的请求参数
    public static final int UNAUTHORIZED = 401;            // 未登录或登录已过期
    public static final int FORBIDDEN = 403;               // 没有权限
    public static final int NOT_FOUND = 404;               // 资源不存在
    public static final int METHOD_NOT_ALLOWED = 405;      // 请求方法不允许
    public static final int TOO_MANY_REQUESTS = 429;       // 请求过于频繁
    
    /** 用户模块错误码：10001-10999 */
    public static final int USER_NOT_FOUND = 10001;        // 用户不存在
    public static final int USER_PASSWORD_ERROR = 10002;   // 密码错误
    public static final int USER_ACCOUNT_LOCKED = 10003;   // 账号已被锁定
    public static final int USER_ACCOUNT_DISABLED = 10004; // 账号已被禁用
    public static final int USER_USERNAME_EXISTS = 10005;  // 用户名已存在
    public static final int USER_EMAIL_EXISTS = 10006;     // 邮箱已存在
    public static final int USER_PHONE_EXISTS = 10007;     // 手机号已存在
    public static final int USER_OLD_PASSWORD_ERROR = 10008; // 原密码错误
    public static final int USER_ROLE_NOT_EXISTS = 10009;  // 角色不存在
    
    /** 文章模块错误码：11001-11999 */
    public static final int POST_NOT_FOUND = 11001;        // 文章不存在
    public static final int POST_ALREADY_PUBLISHED = 11002; // 文章已发布
    public static final int POST_ALREADY_DELETED = 11003;  // 文章已删除
    public static final int POST_TITLE_EXISTS = 11004;     // 文章标题已存在
    public static final int POST_SLUG_EXISTS = 11005;      // 文章别名已存在
    public static final int POST_CATEGORY_NOT_EXISTS = 11006; // 文章分类不存在
    
    /** 标签模块错误码：12001-12999 */
    public static final int TAG_NOT_FOUND = 12001;         // 标签不存在
    public static final int TAG_NAME_EXISTS = 12002;       // 标签名称已存在
    public static final int TAG_ALREADY_DELETED = 12003;   // 标签已删除
    
    /** 分类模块错误码：13001-13999 */
    public static final int CATEGORY_NOT_FOUND = 13001;    // 分类不存在
    public static final int CATEGORY_NAME_EXISTS = 13002;  // 分类名称已存在
    public static final int CATEGORY_PARENT_NOT_EXISTS = 13003; // 父分类不存在
    public static final int CATEGORY_HAS_CHILDREN = 13004; // 分类下有子分类
    public static final int CATEGORY_HAS_POSTS = 13005;    // 分类下有文章
    
    /** 评论模块错误码：14001-14999 */
    public static final int COMMENT_NOT_FOUND = 14001;     // 评论不存在
    public static final int COMMENT_DISABLED = 14002;      // 评论功能已关闭
    public static final int COMMENT_CONTENT_INVALID = 14003; // 评论内容不合法
    
    /** 文件模块错误码：15001-15999 */
    public static final int FILE_NOT_FOUND = 15001;        // 文件不存在
    public static final int FILE_UPLOAD_FAILED = 15002;    // 文件上传失败
    public static final int FILE_SIZE_LIMIT = 15003;       // 文件大小超出限制
    public static final int FILE_TYPE_NOT_ALLOWED = 15004; // 文件类型不允许
    
    /** 配置模块错误码：16001-16999 */
    public static final int CONFIG_NOT_FOUND = 16001;      // 配置不存在
    public static final int CONFIG_KEY_EXISTS = 16002;     // 配置键已存在
    
    /** 统计模块错误码：17001-17999 */
    public static final int STATS_PARAMETER_INVALID = 17001; // 统计参数无效
    
    /** 第三方服务错误码：18001-18999 */
    public static final int THIRD_SERVICE_ERROR = 18001;   // 第三方服务异常
    public static final int EMAIL_SEND_FAILED = 18002;     // 邮件发送失败
    public static final int SMS_SEND_FAILED = 18003;       // 短信发送失败
    
    /** 验证码错误码：19001-19999 */
    public static final int CAPTCHA_EXPIRED = 19001;       // 验证码已过期
    public static final int CAPTCHA_INCORRECT = 19002;     // 验证码不正确
    
    /** 其他错误码：90001-99999 */
    public static final int PARAM_ERROR = 90001;           // 参数错误
    public static final int DATA_NOT_EXISTS = 90002;       // 数据不存在
    public static final int DATA_ALREADY_EXISTS = 90003;   // 数据已存在
    public static final int OPERATION_FAILED = 90004;      // 操作失败
    public static final int SYSTEM_BUSY = 90005;           // 系统繁忙
} 