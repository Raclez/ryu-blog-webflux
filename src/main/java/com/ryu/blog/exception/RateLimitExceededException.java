package com.ryu.blog.exception;

import com.ryu.blog.constant.ErrorCodeConstants;
import lombok.Getter;

/**
 * 速率限制超出异常
 * 
 * <p>当用户超过配额限制时抛出此异常。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Getter
public class RateLimitExceededException extends BaseException {

    private static final long serialVersionUID = 1L;

    private final String limitType;
    private final Integer limit;
    private final Integer used;

    public RateLimitExceededException(String message) {
        super(ErrorCodeConstants.TOO_MANY_REQUESTS, message);
        this.limitType = null;
        this.limit = null;
        this.used = null;
    }

    public RateLimitExceededException(String limitType, Integer limit, Integer used) {
        super(ErrorCodeConstants.TOO_MANY_REQUESTS, 
              String.format("超过%s限制：已使用 %d/%d 次", limitType, used, limit));
        this.limitType = limitType;
        this.limit = limit;
        this.used = used;
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(ErrorCodeConstants.TOO_MANY_REQUESTS, message, cause);
        this.limitType = null;
        this.limit = null;
        this.used = null;
    }
}
