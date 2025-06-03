package com.ryu.blog.config;

import com.ryu.blog.exception.ValidatorExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * 异常处理配置类
 * 
 * @author ryu
 */
@Configuration
public class ExceptionHandlerConfig {
    
    /**
     * 注册校验器
     * 
     * @return 校验器
     */
    @Bean
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }
    
    /**
     * 注册自定义校验异常处理器
     * 
     * @param validator 校验器
     * @return 自定义校验异常处理器
     */
    @Bean
    public ValidatorExceptionHandler validatorExceptionHandler(Validator validator) {
        return new ValidatorExceptionHandler(validator);
    }
} 