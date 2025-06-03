package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;

/**
 * 第三方服务异常
 * 
 * @author ryu
 */
public class ThirdPartyServiceException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public ThirdPartyServiceException(String message) {
        super(ErrorCodeConstants.THIRD_SERVICE_ERROR, message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原始异常
     */
    public ThirdPartyServiceException(String message, Throwable cause) {
        super(ErrorCodeConstants.THIRD_SERVICE_ERROR, message, cause);
    }
    
    /**
     * 邮件发送失败异常
     * 
     * @param message 错误消息
     * @return 第三方服务异常实例
     */
    public static ThirdPartyServiceException emailSendFailed(String message) {
        return new ThirdPartyServiceException("邮件发送失败：" + message);
    }
    
    /**
     * 短信发送失败异常
     * 
     * @param message 错误消息
     * @return 第三方服务异常实例
     */
    public static ThirdPartyServiceException smsSendFailed(String message) {
        return new ThirdPartyServiceException("短信发送失败：" + message);
    }
    
    /**
     * 文件上传失败异常
     * 
     * @param message 错误消息
     * @return 第三方服务异常实例
     */
    public static ThirdPartyServiceException fileUploadFailed(String message) {
        return new ThirdPartyServiceException("文件上传失败：" + message);
    }
} 