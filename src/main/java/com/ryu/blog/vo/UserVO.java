package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息视图对象
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息视图对象")
public class UserVO {
    
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "电子邮件")
    private String email;
    
    @Schema(description = "昵称")
    private String nickname;
    
    @Schema(description = "用户简介")
    private String bio;
    
    @Schema(description = "用户头像")
    private String avatar;
    
    @Schema(description = "用户状态：0-禁用，1-正常，2-锁定")
    private Integer status;
    
    @Schema(description = "用户手机号")
    private String phone;
    
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    @Schema(description = "最后登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLogin;
    
    @Schema(description = "最后登录IP")
    private String lastLoginIp;
    
    @Schema(description = "用户角色ID列表")
    private List<Long> roleIds;
    
    @Schema(description = "用户角色名称列表")
    private List<String> roles;
} 