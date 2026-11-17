package com.ryu.blog.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * JWT 密钥校验
 *
 * <p>Sa-Token 的 JWT 模式下，密钥为空会<b>回退到内置的公开默认密钥</b> ——
 * 那意味着任何人可以签发任意 userId 的合法 token，直接冒充 admin。
 * 而 {@code application.yml} 里写的是 {@code jwt-secret-key: ${JWT_SECRET}}，
 * 没有默认值 —— 缺失时解析成空串，本校验器直接拒绝启动。
 *
 * <p><b>两级策略</b>：
 * <ul>
 *   <li><b>密钥为空</b> —— 任何环境都拒绝启动。这是致命情况：
 *       等于把「可伪造 admin token」的钥匙挂在网上。</li>
 *   <li><b>密钥过短</b>（&lt; 32 字节）—— <b>prod 环境拒绝启动</b>，
 *       其它环境打一条醒目的 ERROR 日志但放行。
 *       理由：短密钥确实弱（暴力空间小），但直接让开发环境起不来
 *       只会让人把校验关掉；生产环境才是必须守住的那道。</li>
 * </ul>
 *
 * <p>{@code .env} 由 Spring Boot 的 {@code spring.config.import} 在
 * <b>ConfigData 阶段</b>导入 —— 早于本 bean 的创建，所以这里读到的已经是最终值。
 * （早先这里靠 {@code @DependsOn("dotenvLoader")} 兜底，那套是
 * {@code @Component} + {@code @PostConstruct}，注入时机太晚，已随它一并移除。）
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecretValidator {

    /** 生产环境要求的最小密钥长度（字节） */
    private static final int MIN_SECRET_LENGTH = 32;

    private final Environment environment;

    @PostConstruct
    public void validate() {
        String secret = environment.getProperty("sa-token.jwt-secret-key", "");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 密钥未配置：请设置环境变量 JWT_SECRET（建议至少 " + MIN_SECRET_LENGTH + " 字节）。"
                            + "为空时 Sa-Token 会回退到内置的公开默认密钥，任何人都能伪造 admin 的 token。");
        }

        if (secret.length() < MIN_SECRET_LENGTH) {
            String message = "JWT 密钥过短（当前 " + secret.length() + " 字节，建议至少 " + MIN_SECRET_LENGTH
                    + " 字节）—— 暴力空间偏小，建议尽快轮换";
            if (isProduction()) {
                throw new IllegalStateException(message + "；生产环境必须使用足够强的密钥。");
            }
            log.error("⚠️ {}", message);
            return;
        }
        log.info("JWT 密钥校验通过（长度 {} 字节）", secret.length());
    }

    private boolean isProduction() {
        String[] profiles = environment.getActiveProfiles();
        for (String profile : profiles) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
