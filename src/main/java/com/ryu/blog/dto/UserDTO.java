package com.ryu.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户数据传输对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "用户数据传输对象")
public class UserDTO {
    
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "密码")
    private String password;
    
    @Schema(description = "昵称")
    private String nickname;
    
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;
    
    @Schema(description = "手机号")
    private String phone;
    
    @Schema(description = "头像")
    private String avatar;
    
    @Schema(description = "个人简介")
    private String bio;
    
    @Schema(description = "用户状态：0-禁用，1-正常，2-锁定")
    private Integer status;
    
    @Schema(description = "角色ID列表")
    private List<Long> roleIds;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后登录时间")
    private LocalDateTime lastLogin;
    
    @Schema(description = "最后登录IP")
    private String lastLoginIp;
} 