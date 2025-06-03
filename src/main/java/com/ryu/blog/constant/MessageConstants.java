package com.ryu.blog.constant;

/**
 * 错误消息常量
 * 
 * @author ryu
 */
public class MessageConstants {
    
    /** 通用消息 */
    public static final String SUCCESS = "操作成功";
    public static final String ERROR = "系统内部错误";
    public static final String PARAM_ERROR = "参数错误";
    public static final String DATA_NOT_EXISTS = "数据不存在";
    public static final String DATA_ALREADY_EXISTS = "数据已存在";
    public static final String OPERATION_FAILED = "操作失败";
    public static final String SYSTEM_BUSY = "系统繁忙，请稍后再试";
    
    /** 认证相关消息 */
    public static final String UNAUTHORIZED = "未登录或登录已过期";
    public static final String FORBIDDEN = "没有权限";
    public static final String NOT_FOUND = "请求的资源不存在";
    public static final String METHOD_NOT_ALLOWED = "请求方法不允许";
    public static final String TOO_MANY_REQUESTS = "请求过于频繁，请稍后再试";
    
    /** 用户相关消息 */
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String USER_PASSWORD_ERROR = "密码错误";
    public static final String USER_ACCOUNT_LOCKED = "账号已被锁定";
    public static final String USER_ACCOUNT_DISABLED = "账号已被禁用";
    public static final String USER_USERNAME_EXISTS = "用户名已存在";
    public static final String USER_EMAIL_EXISTS = "邮箱已存在";
    public static final String USER_PHONE_EXISTS = "手机号已存在";
    public static final String USER_OLD_PASSWORD_ERROR = "原密码错误";
    public static final String USER_ROLE_NOT_EXISTS = "角色不存在";
    public static final String USER_CREATE_SUCCESS = "用户创建成功";
    public static final String USER_UPDATE_SUCCESS = "用户信息更新成功";
    public static final String USER_DELETE_SUCCESS = "用户删除成功";
    public static final String USER_PASSWORD_RESET_SUCCESS = "密码重置成功";
    
    /** 文章相关消息 */
    public static final String POST_NOT_FOUND = "文章不存在";
    public static final String POST_ALREADY_PUBLISHED = "文章已发布";
    public static final String POST_ALREADY_DELETED = "文章已删除";
    public static final String POST_TITLE_EXISTS = "文章标题已存在";
    public static final String POST_SLUG_EXISTS = "文章别名已存在";
    public static final String POST_CATEGORY_NOT_EXISTS = "文章分类不存在";
    public static final String POST_CREATE_SUCCESS = "文章创建成功";
    public static final String POST_UPDATE_SUCCESS = "文章更新成功";
    public static final String POST_DELETE_SUCCESS = "文章删除成功";
    public static final String POST_PUBLISH_SUCCESS = "文章发布成功";
    public static final String POST_UNPUBLISH_SUCCESS = "文章取消发布成功";
    
    /** 标签相关消息 */
    public static final String TAG_NOT_FOUND = "标签不存在";
    public static final String TAG_NAME_EXISTS = "标签名称已存在";
    public static final String TAG_ALREADY_DELETED = "标签已删除";
    public static final String TAG_CREATE_SUCCESS = "标签创建成功";
    public static final String TAG_UPDATE_SUCCESS = "标签更新成功";
    public static final String TAG_DELETE_SUCCESS = "标签删除成功";
    public static final String TAG_ASSIGN_SUCCESS = "标签分配成功";
    public static final String TAG_REMOVE_SUCCESS = "标签移除成功";
    
    /** 分类相关消息 */
    public static final String CATEGORY_NOT_FOUND = "分类不存在";
    public static final String CATEGORY_NAME_EXISTS = "分类名称已存在";
    public static final String CATEGORY_PARENT_NOT_EXISTS = "父分类不存在";
    public static final String CATEGORY_HAS_CHILDREN = "分类下有子分类，不能删除";
    public static final String CATEGORY_HAS_POSTS = "分类下有文章，不能删除";
    public static final String CATEGORY_CREATE_SUCCESS = "分类创建成功";
    public static final String CATEGORY_UPDATE_SUCCESS = "分类更新成功";
    public static final String CATEGORY_DELETE_SUCCESS = "分类删除成功";
    
    /** 评论相关消息 */
    public static final String COMMENT_NOT_FOUND = "评论不存在";
    public static final String COMMENT_DISABLED = "评论功能已关闭";
    public static final String COMMENT_CONTENT_INVALID = "评论内容不合法";
    public static final String COMMENT_CREATE_SUCCESS = "评论发表成功";
    public static final String COMMENT_UPDATE_SUCCESS = "评论更新成功";
    public static final String COMMENT_DELETE_SUCCESS = "评论删除成功";
    public static final String COMMENT_APPROVE_SUCCESS = "评论审核通过";
    public static final String COMMENT_REJECT_SUCCESS = "评论审核拒绝";
    
    /** 文件相关消息 */
    public static final String FILE_NOT_FOUND = "文件不存在";
    public static final String FILE_UPLOAD_FAILED = "文件上传失败";
    public static final String FILE_SIZE_LIMIT = "文件大小超出限制";
    public static final String FILE_TYPE_NOT_ALLOWED = "文件类型不允许";
    public static final String FILE_UPLOAD_SUCCESS = "文件上传成功";
    public static final String FILE_DELETE_SUCCESS = "文件删除成功";
    
    /** 配置相关消息 */
    public static final String CONFIG_NOT_FOUND = "配置不存在";
    public static final String CONFIG_KEY_EXISTS = "配置键已存在";
    public static final String CONFIG_CREATE_SUCCESS = "配置创建成功";
    public static final String CONFIG_UPDATE_SUCCESS = "配置更新成功";
    public static final String CONFIG_DELETE_SUCCESS = "配置删除成功";
    
    /** 验证码相关消息 */
    public static final String CAPTCHA_EXPIRED = "验证码已过期";
    public static final String CAPTCHA_INCORRECT = "验证码不正确";
    
    /** 字典类型相关消息 */
    public static final String DICT_TYPE_NOT_FOUND = "字典类型不存在";
    public static final String DICT_TYPE_CODE_EXISTS = "字典类型编码已存在";
    public static final String DICT_TYPE_CREATE_SUCCESS = "字典类型创建成功";
    public static final String DICT_TYPE_UPDATE_SUCCESS = "字典类型更新成功";
    public static final String DICT_TYPE_DELETE_SUCCESS = "字典类型删除成功";
    public static final String DICT_TYPE_BATCH_DELETE_SUCCESS = "批量删除字典类型成功";
    
    /** 字典项相关消息 */
    public static final String DICT_ITEM_NOT_FOUND = "字典项不存在";
    public static final String DICT_ITEM_KEY_EXISTS = "字典项键已存在";
    public static final String DICT_ITEM_CREATE_SUCCESS = "字典项创建成功";
    public static final String DICT_ITEM_UPDATE_SUCCESS = "字典项更新成功";
    public static final String DICT_ITEM_DELETE_SUCCESS = "字典项删除成功";
    public static final String DICT_ITEM_STATUS_UPDATE_SUCCESS = "字典项状态更新成功";
} 