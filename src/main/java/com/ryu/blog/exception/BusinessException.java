package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 业务异常类
 * 用于业务逻辑层的异常处理，返回HTTP 200状态码，通过业务错误码区分不同错误
 * 
 * @author ryu
 */
public class BusinessException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造函数
     * 
     * @param code 错误码
     * @param message 错误消息
     */
    public BusinessException(Integer code, String message) {
        super(code, message);
    }
    
    /**
     * 构造函数
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param detailMessage 错误详细信息
     */
    public BusinessException(Integer code, String message, String detailMessage) {
        super(code, message, detailMessage);
    }
    
    /**
     * 构造函数
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原始异常
     */
    public BusinessException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(ErrorCodeConstants.OPERATION_FAILED, message);
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     */
    public BusinessException(Integer errorCode) {
        super(errorCode, MessageConstants.OPERATION_FAILED);
    }
    
    // ==================== 用户相关异常 ====================
    
    /**
     * 用户不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException userNotFound() {
        return new BusinessException(ErrorCodeConstants.USER_NOT_FOUND, MessageConstants.USER_NOT_FOUND);
    }
    
    /**
     * 用户名已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException usernameExists() {
        return new BusinessException(ErrorCodeConstants.USER_USERNAME_EXISTS, MessageConstants.USER_USERNAME_EXISTS);
    }
    
    /**
     * 邮箱已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException emailExists() {
        return new BusinessException(ErrorCodeConstants.USER_EMAIL_EXISTS, MessageConstants.USER_EMAIL_EXISTS);
    }
    
    /**
     * 手机号已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException phoneExists() {
        return new BusinessException(ErrorCodeConstants.USER_PHONE_EXISTS, MessageConstants.USER_PHONE_EXISTS);
    }
    
    /**
     * 密码错误异常
     * 
     * @return 异常实例
     */
    public static BusinessException passwordError() {
        return new BusinessException(ErrorCodeConstants.USER_PASSWORD_ERROR, MessageConstants.USER_PASSWORD_ERROR);
    }
    
    /**
     * 旧密码错误异常
     * 
     * @return 异常实例
     */
    public static BusinessException oldPasswordError() {
        return new BusinessException(ErrorCodeConstants.USER_OLD_PASSWORD_ERROR, MessageConstants.USER_OLD_PASSWORD_ERROR);
    }
    
    /**
     * 账号已被禁用异常
     * 
     * @return 异常实例
     */
    public static BusinessException accountDisabled() {
        return new BusinessException(ErrorCodeConstants.USER_ACCOUNT_DISABLED, MessageConstants.USER_ACCOUNT_DISABLED);
    }
    
    /**
     * 账号已被锁定异常
     * 
     * @return 异常实例
     */
    public static BusinessException accountLocked() {
        return new BusinessException(ErrorCodeConstants.USER_ACCOUNT_LOCKED, MessageConstants.USER_ACCOUNT_LOCKED);
    }
    
    /**
     * 登录失败异常
     * 
     * @return 异常实例
     */
    public static BusinessException loginFailed() {
        return new BusinessException(ErrorCodeConstants.USER_LOGIN_FAILED, MessageConstants.USER_LOGIN_FAILED);
    }
    
    /**
     * 角色不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException roleNotExists() {
        return new BusinessException(ErrorCodeConstants.USER_ROLE_NOT_EXISTS, MessageConstants.USER_ROLE_NOT_EXISTS);
    }
    
    // ==================== 文章相关异常 ====================
    
    /**
     * 文章不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException postNotFound() {
        return new BusinessException(ErrorCodeConstants.POST_NOT_FOUND, MessageConstants.POST_NOT_FOUND);
    }
    
    /**
     * 文章已发布异常
     * 
     * @return 异常实例
     */
    public static BusinessException postAlreadyPublished() {
        return new BusinessException(ErrorCodeConstants.POST_ALREADY_PUBLISHED, MessageConstants.POST_ALREADY_PUBLISHED);
    }
    
    /**
     * 文章已删除异常
     * 
     * @return 异常实例
     */
    public static BusinessException postAlreadyDeleted() {
        return new BusinessException(ErrorCodeConstants.POST_ALREADY_DELETED, MessageConstants.POST_ALREADY_DELETED);
    }
    
    /**
     * 文章标题已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException postTitleExists() {
        return new BusinessException(ErrorCodeConstants.POST_TITLE_EXISTS, MessageConstants.POST_TITLE_EXISTS);
    }
    
    /**
     * 文章别名已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException postSlugExists() {
        return new BusinessException(ErrorCodeConstants.POST_SLUG_EXISTS, MessageConstants.POST_SLUG_EXISTS);
    }
    
    /**
     * 文章分类不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException postCategoryNotExists() {
        return new BusinessException(ErrorCodeConstants.POST_CATEGORY_NOT_EXISTS, MessageConstants.POST_CATEGORY_NOT_EXISTS);
    }
    
    /**
     * 文章版本不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException postVersionNotFound() {
        return new BusinessException(ErrorCodeConstants.POST_NOT_FOUND, "文章版本不存在");
    }
    
    /**
     * 文章版本不存在异常（带版本号）
     * 
     * @param version 版本号
     * @return 异常实例
     */
    public static BusinessException postVersionNotFound(Integer version) {
        return new BusinessException(ErrorCodeConstants.POST_NOT_FOUND, "版本 " + version + " 不存在");
    }
    
    /**
     * 文章没有版本记录异常
     * 
     * @return 异常实例
     */
    public static BusinessException postNoVersionHistory() {
        return new BusinessException(ErrorCodeConstants.POST_NOT_FOUND, "文章没有版本记录");
    }
    
    // ==================== 标签相关异常 ====================
    
    /**
     * 标签不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException tagNotFound() {
        return new BusinessException(ErrorCodeConstants.TAG_NOT_FOUND, MessageConstants.TAG_NOT_FOUND);
    }
    
    /**
     * 标签名称已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException tagNameExists() {
        return new BusinessException(ErrorCodeConstants.TAG_NAME_EXISTS, MessageConstants.TAG_NAME_EXISTS);
    }
    
    /**
     * 标签已删除异常
     * 
     * @return 异常实例
     */
    public static BusinessException tagAlreadyDeleted() {
        return new BusinessException(ErrorCodeConstants.TAG_ALREADY_DELETED, MessageConstants.TAG_ALREADY_DELETED);
    }
    
    // ==================== 分类相关异常 ====================
    
    /**
     * 分类不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException categoryNotFound() {
        return new BusinessException(ErrorCodeConstants.CATEGORY_NOT_FOUND, MessageConstants.CATEGORY_NOT_FOUND);
    }
    
    /**
     * 分类名称已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException categoryNameExists() {
        return new BusinessException(ErrorCodeConstants.CATEGORY_NAME_EXISTS, MessageConstants.CATEGORY_NAME_EXISTS);
    }
    
    /**
     * 父分类不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException categoryParentNotExists() {
        return new BusinessException(ErrorCodeConstants.CATEGORY_PARENT_NOT_EXISTS, MessageConstants.CATEGORY_PARENT_NOT_EXISTS);
    }
    
    /**
     * 分类下有子分类异常
     * 
     * @return 异常实例
     */
    public static BusinessException categoryHasChildren() {
        return new BusinessException(ErrorCodeConstants.CATEGORY_HAS_CHILDREN, MessageConstants.CATEGORY_HAS_CHILDREN);
    }
    
    /**
     * 分类下有文章异常
     * 
     * @return 异常实例
     */
    public static BusinessException categoryHasPosts() {
        return new BusinessException(ErrorCodeConstants.CATEGORY_HAS_POSTS, MessageConstants.CATEGORY_HAS_POSTS);
    }
    
    // ==================== 评论相关异常 ====================
    
    /**
     * 评论不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException commentNotFound() {
        return new BusinessException(ErrorCodeConstants.COMMENT_NOT_FOUND, MessageConstants.COMMENT_NOT_FOUND);
    }
    
    /**
     * 评论功能已关闭异常
     * 
     * @return 异常实例
     */
    public static BusinessException commentDisabled() {
        return new BusinessException(ErrorCodeConstants.COMMENT_DISABLED, MessageConstants.COMMENT_DISABLED);
    }
    
    /**
     * 评论内容不合法异常
     * 
     * @return 异常实例
     */
    public static BusinessException commentContentInvalid() {
        return new BusinessException(ErrorCodeConstants.COMMENT_CONTENT_INVALID, MessageConstants.COMMENT_CONTENT_INVALID);
    }
    
    // ==================== 文件相关异常 ====================
    
    /**
     * 文件不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException fileNotFound() {
        return new BusinessException(ErrorCodeConstants.FILE_NOT_FOUND, MessageConstants.FILE_NOT_FOUND);
    }
    
    /**
     * 文件上传失败异常
     * 
     * @return 异常实例
     */
    public static BusinessException fileUploadFailed() {
        return new BusinessException(ErrorCodeConstants.FILE_UPLOAD_FAILED, MessageConstants.FILE_UPLOAD_FAILED);
    }
    
    /**
     * 文件上传失败异常（带详细信息）
     * 
     * @param detail 详细信息
     * @return 异常实例
     */
    public static BusinessException fileUploadFailed(String detail) {
        return new BusinessException(ErrorCodeConstants.FILE_UPLOAD_FAILED, MessageConstants.FILE_UPLOAD_FAILED, detail);
    }
    
    /**
     * 文件大小超出限制异常
     * 
     * @return 异常实例
     */
    public static BusinessException fileSizeLimit() {
        return new BusinessException(ErrorCodeConstants.FILE_SIZE_LIMIT, MessageConstants.FILE_SIZE_LIMIT);
    }
    
    /**
     * 文件类型不允许异常
     * 
     * @return 异常实例
     */
    public static BusinessException fileTypeNotAllowed() {
        return new BusinessException(ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED, MessageConstants.FILE_TYPE_NOT_ALLOWED);
    }
    
    // ==================== 配置相关异常 ====================
    
    /**
     * 配置不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException configNotFound() {
        return new BusinessException(ErrorCodeConstants.CONFIG_NOT_FOUND, MessageConstants.CONFIG_NOT_FOUND);
    }
    
    /**
     * 配置键已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException configKeyExists() {
        return new BusinessException(ErrorCodeConstants.CONFIG_KEY_EXISTS, MessageConstants.CONFIG_KEY_EXISTS);
    }
    
    // ==================== 验证码相关异常 ====================
    
    /**
     * 验证码过期异常
     * 
     * @return 异常实例
     */
    public static BusinessException captchaExpired() {
        return new BusinessException(ErrorCodeConstants.CAPTCHA_EXPIRED, MessageConstants.CAPTCHA_EXPIRED);
    }
    
    /**
     * 验证码错误异常
     * 
     * @return 异常实例
     */
    public static BusinessException captchaIncorrect() {
        return new BusinessException(ErrorCodeConstants.CAPTCHA_INCORRECT, MessageConstants.CAPTCHA_INCORRECT);
    }
    
    // ==================== 字典相关异常 ====================
    
    /**
     * 字典类型不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException dictTypeNotFound() {
        return new BusinessException(ErrorCodeConstants.DICT_TYPE_NOT_FOUND, MessageConstants.DICT_TYPE_NOT_FOUND);
    }
    
    /**
     * 字典类型编码已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException dictTypeCodeExists() {
        return new BusinessException(ErrorCodeConstants.DICT_TYPE_CODE_EXISTS, MessageConstants.DICT_TYPE_CODE_EXISTS);
    }
    
    /**
     * 字典项不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException dictItemNotFound() {
        return new BusinessException(ErrorCodeConstants.DICT_ITEM_NOT_FOUND, MessageConstants.DICT_ITEM_NOT_FOUND);
    }
    
    /**
     * 字典项键已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException dictItemKeyExists() {
        return new BusinessException(ErrorCodeConstants.DICT_ITEM_KEY_EXISTS, MessageConstants.DICT_ITEM_KEY_EXISTS);
    }
    
    // ==================== 通用异常 ====================
    
    /**
     * 参数错误异常
     * 
     * @param message 错误消息
     * @return 异常实例
     */
    public static BusinessException paramError(String message) {
        return new BusinessException(ErrorCodeConstants.PARAM_ERROR, message);
    }
    
    /**
     * 数据不存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException dataNotExists() {
        return new BusinessException(ErrorCodeConstants.DATA_NOT_EXISTS, MessageConstants.DATA_NOT_EXISTS);
    }
    
    /**
     * 数据已存在异常
     * 
     * @return 异常实例
     */
    public static BusinessException dataAlreadyExists() {
        return new BusinessException(ErrorCodeConstants.DATA_ALREADY_EXISTS, MessageConstants.DATA_ALREADY_EXISTS);
    }
    
    /**
     * 操作失败异常
     * 
     * @return 异常实例
     */
    public static BusinessException operationFailed() {
        return new BusinessException(ErrorCodeConstants.OPERATION_FAILED, MessageConstants.OPERATION_FAILED);
    }
    
    /**
     * 操作失败异常（带详细信息）
     * 
     * @param message 错误消息
     * @return 异常实例
     */
    public static BusinessException operationFailed(String message) {
        return new BusinessException(ErrorCodeConstants.OPERATION_FAILED, message);
    }
    
    /**
     * 系统繁忙异常
     * 
     * @return 异常实例
     */
    public static BusinessException systemBusy() {
        return new BusinessException(ErrorCodeConstants.SYSTEM_BUSY, MessageConstants.SYSTEM_BUSY);
    }
} 