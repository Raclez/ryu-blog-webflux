package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息视图对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息视图对象")
public class UserInfoVO {
    
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "昵称")
    private String nickname;
    
    @Schema(description = "头像")
    private String avatar;
    
    @Schema(description = "邮箱")
    private String email;
    
    @Schema(description = "手机号")
    private String phone;
    
    @Schema(description = "个人简介")
    private String bio;
    
    @Schema(description = "用户状态")
    private Integer status;
    
    @Schema(description = "最后登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLogin;
    
    @Schema(description = "最后登录IP")
    private String lastLoginIp;
    
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @Schema(description = "角色列表")
    private List<RoleVO> roles;
    
    @Schema(description = "权限标识列表")
    private List<String> permissions;
    
    /**
     * 角色信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String name;
        private String code;
    }
} 