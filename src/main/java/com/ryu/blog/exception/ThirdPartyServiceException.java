package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;

/**
 * 第三方服务异常
 * 用于第三方服务调用失败场景
 * 适用场景：邮件发送、短信发送、文件上传、外部API调用等
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
     * 构造函数
     * 
     * @param message 错误消息
     * @param detailMessage 详细错误信息
     */
    public ThirdPartyServiceException(String message, String detailMessage) {
        super(ErrorCodeConstants.THIRD_SERVICE_ERROR, message, detailMessage);
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
    
    /**
     * 外部API调用失败异常
     * 
     * @param serviceName 服务名称
     * @param message 错误消息
     * @return 第三方服务异常实例
     */
    public static ThirdPartyServiceException apiCallFailed(String serviceName, String message) {
        return new ThirdPartyServiceException(String.format("%s服务调用失败：%s", serviceName, message));
    }
    
    /**
     * 第三方服务超时异常
     * 
     * @param serviceName 服务名称
     * @return 第三方服务异常实例
     */
    public static ThirdPartyServiceException timeout(String serviceName) {
        return new ThirdPartyServiceException(serviceName + "服务响应超时");
    }
    
    /**
     * 第三方服务不可用异常
     * 
     * @param serviceName 服务名称
     * @return 第三方服务异常实例
     */
    public static ThirdPartyServiceException unavailable(String serviceName) {
        return new ThirdPartyServiceException(serviceName + "服务暂时不可用");
    }
} 