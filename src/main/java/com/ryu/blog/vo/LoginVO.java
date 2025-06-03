package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录响应视图对象
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应信息")
public class LoginVO {
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "昵称")
    private String nickname;
    
    @Schema(description = "头像URL")
    private String avatar;
    
    @Schema(description = "访问令牌")
    private String accessToken;
    
    @Schema(description = "令牌类型，通常为Bearer")
    private String tokenType;
    
    @Schema(description = "令牌有效期（秒）")
    private Long expiresIn;
    
    @Schema(description = "令牌过期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
    
    @Schema(description = "刷新令牌，用于获取新的访问令牌")
    private String refreshToken;
    
    @Schema(description = "最后登录IP")
    private String lastLoginIp;
    
    @Schema(description = "用户角色列表")
    private List<String> roles;
    
    @Schema(description = "用户权限列表")
    private List<String> permissions;
} 