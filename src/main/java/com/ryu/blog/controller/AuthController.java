package com.ryu.blog.controller;

import cn.hutool.crypto.SecureUtil;
import com.ryu.blog.dto.LoginDTO;
import com.ryu.blog.entity.User;
import com.ryu.blog.service.UserService;
import com.ryu.blog.utils.Result;
import com.ryu.blog.utils.SaTokenUtils;
import com.ryu.blog.vo.LoginVO;
import com.wf.captcha.SpecCaptcha;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * 认证控制器
 * @author ryu
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户认证相关接口")
public class AuthController {

    private final UserService userService;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final String BEARER_TOKEN_TYPE = "Bearer";
    private static final String UNKNOWN_IP = "unknown";

    @Operation(summary = "用户注册", description = "用户注册")
    @PostMapping("/register")
    public Mono<Result<User>> register(@RequestBody @Validated User user) {
        return userService.register(user)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "用户登录", description = "用户登录")
    @PostMapping("/login")
    public Mono<Result<LoginVO>> login(@RequestBody @Validated LoginDTO loginDTO, ServerWebExchange exchange) {
        // 先验证验证码
        return verifyCaptcha(loginDTO)
                .flatMap(valid -> {
                    if (!valid && loginDTO.getCaptchaId() != null) {
                        return Mono.just(Result.error("验证码错误或已过期"));
                    }
                    
                    // 验证用户身份
                    return authenticateUser(loginDTO, exchange);
                });
    }
    
    /**
     * 验证验证码
     * @param loginDTO 登录DTO
     * @return 验证结果
     */
    private Mono<Boolean> verifyCaptcha(LoginDTO loginDTO) {
        // 如果未提供验证码信息，直接返回验证通过
        if (loginDTO.getCaptchaId() == null || loginDTO.getCaptcha() == null) {
            return Mono.just(true);
        }
        
        String captchaKey = CAPTCHA_PREFIX + loginDTO.getCaptchaId();
        
        // 从Redis获取验证码并验证
        return reactiveRedisTemplate.opsForValue().get(captchaKey)
                .map(code -> code.toString().equalsIgnoreCase(loginDTO.getCaptcha()))
                .defaultIfEmpty(false)
                .flatMap(valid -> {
                    // 验证成功后删除Redis中的验证码
                    if (valid) {
                        return reactiveRedisTemplate.delete(captchaKey).thenReturn(true);
                    }
                    return Mono.just(false);
                });
    }
    
    /**
     * 验证用户身份
     * @param loginDTO 登录DTO
     * @param exchange 服务器交换器
     * @return 验证结果
     */
    private Mono<Result<LoginVO>> authenticateUser(LoginDTO loginDTO, ServerWebExchange exchange) {
        return userService.getUserByUsername(loginDTO.getUsername())
                .flatMap(user -> {
                    // 检查用户状态
                    if (user.getStatus() == 0) {
                        return Mono.<Result<LoginVO>>just(Result.error("账号已被禁用"));
                    }
                    
                    // 验证密码
                    String encryptedPassword = SecureUtil.md5(loginDTO.getPassword());
                    if (!encryptedPassword.equals(user.getPassword())) {
                        return Mono.<Result<LoginVO>>just(Result.error("用户名或密码错误"));
                    }
                    
                    // 获取客户端IP
                    String clientIp = getClientIp(exchange);
                    
                    // 登录成功，更新最后登录时间和IP
                    return userService.updateLastLogin(user.getId(), clientIp)
                            .flatMap(result -> generateLoginResponse(user, exchange));
                })
                .onErrorResume(e -> {
                    log.error("登录失败: {}", e);
                    return Mono.<Result<LoginVO>>just(Result.error("登录失败，请稍后再试"));
                });
    }
    
    /**
     * 获取客户端IP
     * @param exchange 服务器交换器
     * @return 客户端IP
     */
    private String getClientIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() != null 
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
                : UNKNOWN_IP;
    }
    
    /**
     * 生成登录响应
     * @param user 用户信息
     * @param exchange 服务器交换器
     * @return 登录响应
     */
    private Mono<Result<LoginVO>> generateLoginResponse(User user, ServerWebExchange exchange) {
        // 使用SaTokenUtils执行登录操作
        return SaTokenUtils.login(exchange, user.getId())
                .flatMap(tokenValue -> 
                    SaTokenUtils.getTokenTimeout(exchange)
                        .map(tokenTimeout -> {
                            // 构建登录返回对象
                            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(tokenTimeout);
                            
                            LoginVO loginVO = LoginVO.builder()
                                    .userId(user.getId())
                                    .username(user.getUsername())
                                    .nickname(user.getNickname())
                                    .avatar(user.getAvatar())
                                    .accessToken(tokenValue)
                                    .tokenType(BEARER_TOKEN_TYPE)
                                    .expiresIn(tokenTimeout)
                                    .expiresAt(expiresAt)
                                    .refreshToken(null) // 简化处理，使用相同token
                                    .roles(new ArrayList<>()) // 可从用户角色服务获取
                                    .permissions(new ArrayList<>()) // 可从权限服务获取
                                    .build();
                            
                            return Result.success(loginVO);
                        })
                );
    }

    @Operation(summary = "用户登出", description = "用户登出")
    @PostMapping("/logout")
    public Mono<Result<String>> logout(ServerWebExchange exchange) {
        // 使用SaTokenUtils执行登出操作
        return SaTokenUtils.logout(exchange)
                .thenReturn(Result.success("登出成功"));
    }
    
    @Operation(summary = "获取验证码", description = "获取图形验证码")
    @GetMapping("/captcha")
    public Mono<Result<Map<String, String>>> getCaptcha() {
        // 生成验证码
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 5);
        String code = captcha.text().toLowerCase();
        String key = UUID.randomUUID().toString();
        
        // 将验证码存入Redis，有效期5分钟
        return reactiveRedisTemplate.opsForValue().set(CAPTCHA_PREFIX + key, code, Duration.ofMinutes(5))
                .thenReturn(Result.success(Map.of(
                        "key", key,
                        "image", captcha.toBase64()
                )));
    }

    @Operation(summary = "检查用户名", description = "检查用户名是否已存在")
    @GetMapping("/check/username")
    public Mono<Result<Boolean>> checkUsername(@RequestParam String username) {
        return userService.checkUsernameExists(username)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }

    @Operation(summary = "检查邮箱", description = "检查邮箱是否已存在")
    @GetMapping("/check/email")
    public Mono<Result<Boolean>> checkEmail(@RequestParam String email) {
        return userService.checkEmailExists(email)
                .map(Result::success)
                .onErrorResume(e -> Mono.just(Result.error(e.getMessage())));
    }
} 