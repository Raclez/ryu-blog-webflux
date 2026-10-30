package com.ryu.blog.service.impl.article;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 文章访问密码（{@code t_posts.password}）的编码与校验规则。
 *
 * <p><b>为什么文章访问密码也要哈希</b>：
 * 同一个库里，<b>用户登录密码</b>（{@code t_sys_user.password}）一直是 BCrypt 哈希存的
 * （见 {@code UserServiceImpl} 里的 {@code passwordEncoder}），
 * 而文章访问密码却是<b>明文</b> —— 同一个库两套标准。
 *
 * <p>更要紧的是：文章访问密码是<b>读者自己提供的口令</b>，读者很可能直接复用
 * 自己真正的登录口令。「明文存着」意味着库一旦泄露（备份文件、只读副本、日志），
 * 读者真正的口令也跟着泄露了 —— 这已经不只是「文章被看到」的问题。
 *
 * <p><b>口径</b>：BCrypt（强度 10），与 {@code SaTokenConfig} 里那个
 * {@code BCryptPasswordEncoder} bean 完全一致 —— 两边的哈希互相可验。
 * 本类内部持有一个静态 encoder：{@code BCryptPasswordEncoder} 是线程安全的，
 * 而哈希串里自带强度与盐，任何 encoder 实例都能验。
 *
 * <p><b>历史数据</b>：2026-10-30 之前写入的访问密码是明文。
 * 本类不假设「库里一定已经是哈希」：
 * <ul>
 *   <li>{@link #isPlaintext(String)} 用来识别历史明文；</li>
 *   <li>{@link #matches(String, String)} 对历史明文走常量时间比较，对哈希走 BCrypt 校验；</li>
 *   <li>{@link #rehashIfPlaintext(String)} 让明文在文章下次被保存时顺手洗成哈希
 *       （口令本身不变，文章照样能用同一个口令打开）。</li>
 * </ul>
 *
 * @author ryu
 * @since 1.0.0
 */
public final class ArticleAccessPasswordPolicy {

    /**
     * BCrypt 哈希串的前缀。
     *
     * <p>{@code BCryptPasswordEncoder} 生成的串固定以 {@code $2a$} / {@code $2b$} / {@code $2y$} 开头，
     * 所以「不是这个前缀」就说明是历史明文。
     */
    private static final String BCRYPT_PREFIX = "$2";

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private ArticleAccessPasswordPolicy() {
    }

    /**
     * 编码一个访问密码（明文 → BCrypt 哈希）
     *
     * @param rawPassword 明文口令
     * @return BCrypt 哈希；入参为空白时返回 {@code null}（表示「没有访问密码」）
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return null;
        }
        return ENCODER.encode(rawPassword);
    }

    /**
     * 这个存下来的值是不是「还没编码的历史明文」
     *
     * @param stored 库里的值
     * @return 是历史明文返回 true
     */
    public static boolean isPlaintext(String stored) {
        return stored != null && !stored.isBlank() && !stored.startsWith(BCRYPT_PREFIX);
    }

    /**
     * 校验访问密码
     *
     * <p>兼容两种存法：BCrypt 哈希（正常）与历史明文（2026-10-30 之前）。
     * 历史明文走 {@link MessageDigest#isEqual} 做<b>常量时间</b>比较 ——
     * 逐字节提前返回会泄露「前几位对上了」这类信息。
     *
     * @param rawInput 读者/作者输入的口令
     * @param stored   库里的值
     * @return 口令正确返回 true
     */
    public static boolean matches(String rawInput, String stored) {
        if (rawInput == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (isPlaintext(stored)) {
            return MessageDigest.isEqual(rawInput.getBytes(StandardCharsets.UTF_8),
                    stored.getBytes(StandardCharsets.UTF_8));
        }
        return ENCODER.matches(rawInput, stored);
    }

    /**
     * 顺手把历史明文洗成哈希
     *
     * <p>已经是哈希的值原样返回；空白值归成 {@code null}（没有访问密码）；
     * 历史明文则重新编码 —— <b>口令本身不变</b>，文章照样能用同一个口令打开。
     *
     * @param stored 库里的值
     * @return 该存进库的值
     */
    public static String rehashIfPlaintext(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        return isPlaintext(stored) ? ENCODER.encode(stored) : stored;
    }
}
