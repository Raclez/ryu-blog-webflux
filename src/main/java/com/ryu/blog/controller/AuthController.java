package com.ryu.blog.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.ryu.blog.dto.LoginDTO;
import com.ryu.blog.dto.RegisterDTO;
import com.ryu.blog.entity.User;
import com.ryu.blog.service.UserService;
import com.ryu.blog.service.impl.UserPermissionCache;
import com.ryu.blog.service.impl.UserPermissionResolver;
import com.ryu.blog.utils.Result;
import com.ryu.blog.utils.SaTokenUtils;
import com.ryu.blog.vo.LoginVO;
import com.ryu.blog.vo.UserInfoVO;
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
 *
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
    private final UserPermissionCache permissionCache;
    private final UserPermissionResolver permissionResolver;

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final String BEARER_TOKEN_TYPE = "Bearer";
    private static final String UNKNOWN_IP = "unknown";

    /** 登录失败计数键前缀 */
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    /** 达到该次数后临时锁定，必须换时间窗口再试 */
    private static final long MAX_LOGIN_FAILURES = 5;
    /** 失败计数 / 锁定窗口（秒） */
    private static final long LOGIN_LOCK_SECONDS = 15 * 60;

    @Operation(summary = "用户注册", description = "用户注册")
    @PostMapping("/register")
    public Mono<Result<UserInfoVO>> register(@RequestBody @Validated RegisterDTO registerDTO) {
        // 用专用 DTO：实体里的 id 可被客户端赋值，R2DBC 的 save() 在 id 非空时
        // 走 UPDATE 分支 —— 未认证即可覆盖任意用户行。
        // 返回 VO 而不是实体：实体里带着 BCrypt 密码哈希。
        return userService.register(registerDTO)
                .map(Result::success);
    }
    @SaIgnore
    @Operation(summary = "用户登录", description = "用户登录")
    @PostMapping("/login")
    public Mono<Result<LoginVO>> login(@RequestBody @Validated LoginDTO loginDTO, ServerWebExchange exchange) {
        // 先验证验证码（必填）
        return verifyCaptcha(loginDTO)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.just(Result.error("验证码错误或已过期"));
                    }

                    // 失败计数 / 临时锁定：验证码只能防机器批量撞库，
                    // 挡不住「人工/半自动地反复试同一个账号」，所以还需要计数。
                    return isLoginLocked(loginDTO.getUsername())
                            .flatMap(locked -> {
                                if (locked) {
                                    log.warn("账号 {} 登录失败次数过多，已临时锁定", loginDTO.getUsername());
                                    return Mono.just(Result.error("登录失败次数过多，请 15 分钟后再试"));
                                }

                                // 验证用户身份并生成token；成功清零，失败（含异常）计数
                                return authenticateUser(loginDTO, exchange)
                                        .flatMap(result -> isSuccess(result)
                                                ? clearLoginFailures(loginDTO.getUsername()).thenReturn(result)
                                                : recordLoginFailure(loginDTO.getUsername()).thenReturn(result))
                                        .onErrorResume(error -> recordLoginFailure(loginDTO.getUsername())
                                                .then(Mono.error(error)));
                            });
                });
    }

    /**
     * 验证验证码
     *
     * <p><b>验证码是必填的</b>：原先 {@code captchaId} / {@code captcha} 为 null
     * 就直接返回「验证通过」，登录接口等于完全没有验证码保护。
     *
     * <p>无论校验成败都删掉 Redis 里的验证码 —— 一次性使用，防止拿同一个验证码反复试。
     *
     * @param loginDTO 登录DTO
     * @return 验证结果
     */
    private Mono<Boolean> verifyCaptcha(LoginDTO loginDTO) {
        if (loginDTO.getCaptchaId() == null || loginDTO.getCaptcha() == null
                || loginDTO.getCaptchaId().isBlank() || loginDTO.getCaptcha().isBlank()) {
            return Mono.just(false);
        }

        String captchaKey = CAPTCHA_PREFIX + loginDTO.getCaptchaId();

        return reactiveRedisTemplate.opsForValue().get(captchaKey)
                .map(code -> code.toString().equalsIgnoreCase(loginDTO.getCaptcha()))
                .defaultIfEmpty(false)
                .flatMap(valid -> reactiveRedisTemplate.delete(captchaKey).thenReturn(valid));
    }

    /**
     * 判断账号是否因失败次数过多被临时锁定
     */
    private Mono<Boolean> isLoginLocked(String username) {
        return reactiveRedisTemplate.opsForValue().get(LOGIN_FAIL_PREFIX + username)
                .map(value -> {
                    try {
                        return Long.parseLong(value.toString()) >= MAX_LOGIN_FAILURES;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                })
                .defaultIfEmpty(false);
    }

    /**
     * 记录一次登录失败（INCR + 刷新窗口 TTL）
     */
    private Mono<Void> recordLoginFailure(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        return reactiveRedisTemplate.opsForValue().increment(key)
                .flatMap(count -> reactiveRedisTemplate.expire(key, Duration.ofSeconds(LOGIN_LOCK_SECONDS)))
                .onErrorResume(e -> {
                    log.warn("记录登录失败次数失败: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 登录成功后清除失败计数
     */
    private Mono<Void> clearLoginFailures(String username) {
        return reactiveRedisTemplate.delete(LOGIN_FAIL_PREFIX + username)
                .onErrorResume(e -> {
                    log.warn("清除登录失败计数失败: {}", e.getMessage());
                    return Mono.just(0L);
                })
                .then();
    }

    private boolean isSuccess(Result<?> result) {
        return result != null && Integer.valueOf(200).equals(result.getCode());
    }

    /**
     * 验证用户身份并生成登录响应
     *
     * 职责：
     * - 调用 Service 层验证用户凭证
     * - 更新最后登录信息
     * - 生成 JWT token
     * - 预热该用户的权限快照（供 {@code @SaCheckPermission} 立即可用）
     * - 构建登录响应对象
     *
     * @param loginDTO 登录DTO
     * @param exchange 服务器交换器
     * @return 登录响应
     */
    private Mono<Result<LoginVO>> authenticateUser(LoginDTO loginDTO, ServerWebExchange exchange) {
        // 使用 Service 层的 validateUserCredentials 方法验证用户
        return userService.validateUserCredentials(loginDTO.getUsername(), loginDTO.getPassword())
                .flatMap(user -> {
                    // 获取客户端IP
                    String clientIp = getClientIp(exchange);

                    // 更新最后登录时间和IP，然后生成token
                    return userService.updateLastLogin(user.getId(), clientIp)
                            .then(generateLoginResponse(user, exchange));
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
                                    .roles(new ArrayList<>()) // 登录后填充
                                    .permissions(new ArrayList<>()) // 登录后填充
                                    .build();

                            return Result.success(loginVO);
                        })
                )
                // 填充角色与权限（原先是硬编码的空数组）
                .flatMap(result -> permissionResolver.resolvePermissionIdentities(user.getId())
                        .map(permissions -> {
                            result.getData().setPermissions(new ArrayList<>(permissions));
                            return result;
                        })
                )
                .flatMap(result -> permissionResolver.resolveRoleCodes(user.getId())
                        .map(roles -> {
                            result.getData().setRoles(new ArrayList<>(roles));
                            return result;
                        })
                )
                // 登录时预热权限快照 —— 否则紧接着的第一个受保护请求
                // 会因为快照未就绪而被 fail-closed 拒掉
                .flatMap(result -> permissionCache.refresh(user.getId()).thenReturn(result));
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
                .map(Result::success);
    }

    @Operation(summary = "检查邮箱", description = "检查邮箱是否已存在")
    @GetMapping("/check/email")
    public Mono<Result<Boolean>> checkEmail(@RequestParam String email) {
        return userService.checkEmailExists(email)
                .map(Result::success);
    }
}
