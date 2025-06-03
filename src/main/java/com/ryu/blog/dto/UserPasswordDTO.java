package com.ryu.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户密码修改数据传输对象
 * @author ryu
 */
@Data
@Schema(description = "用户密码修改数据传输对象")
public class UserPasswordDTO {
    
    @NotBlank(message = "旧密码不能为空")
    @Schema(description = "旧密码")
    private String oldPassword;
    
    @NotBlank(message = "新密码不能为空")
    @Schema(description = "新密码")
    private String newPassword;
    
    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认新密码")
    private String confirmPassword;
} 