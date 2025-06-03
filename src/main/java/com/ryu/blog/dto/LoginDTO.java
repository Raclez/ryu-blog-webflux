package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录数据传输对象
 * @author ryu
 */
@Data
@Schema(description = "登录数据传输对象")
public class LoginDTO {
    
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名或邮箱")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;
    
    @Schema(description = "验证码，启用验证码功能时必填")
    private String captcha;
    
    @Schema(description = "验证码ID，与captcha配套使用")
    private String captchaId;
    
    @Schema(description = "是否记住登录状态，延长令牌有效期")
    private Boolean rememberMe = false;
    
    @Schema(description = "登录设备类型")
    private String deviceType;
} 