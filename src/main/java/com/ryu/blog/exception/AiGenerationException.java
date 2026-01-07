package com.ryu.blog.exception;

/**
 * AI内容生成异常
 * 
 * <p>当AI内容生成失败时抛出此异常。
 * 
 * @author Ryu
 * @since 1.0.0
 */
public class AiGenerationException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /** AI生成错误码 */
    public static final int AI_GENERATION_ERROR = 15003;
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public AiGenerationException(String message) {
        super(AI_GENERATION_ERROR, "内容生成失败: " + message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause 原始异常
     */
    public AiGenerationException(String message, Throwable cause) {
        super(AI_GENERATION_ERROR, "内容生成失败: " + message, cause);
    }
    
    /**
     * 生成失败
     */
    public static AiGenerationException generationFailed(String reason) {
        return new AiGenerationException(reason);
    }
    
    /**
     * 内容为空
     */
    public static AiGenerationException emptyContent() {
        return new AiGenerationException("生成的内容为空");
    }
    
    /**
     * 内容格式错误
     */
    public static AiGenerationException invalidFormat(String reason) {
        return new AiGenerationException(String.format("内容格式错误: %s", reason));
    }
}
