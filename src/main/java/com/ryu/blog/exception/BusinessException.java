package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.constant.MessageConstants;

/**
 * 业务异常类
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
     * 密码错误异常
     * 
     * @return 异常实例
     */
    public static BusinessException passwordError() {
        return new BusinessException(ErrorCodeConstants.USER_PASSWORD_ERROR, MessageConstants.USER_PASSWORD_ERROR);
    }
    
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
} 