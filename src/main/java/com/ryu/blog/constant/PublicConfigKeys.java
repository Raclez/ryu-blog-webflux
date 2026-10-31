package com.ryu.blog.constant;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 公开配置白名单
 *
 * <p>决定哪些 {@code t_sys_config} 的键可以经 {@code GET /sysConfig/public}
 * 暴露给未登录的调用方（登录页品牌信息、前台展示类配置）。
 *
 * <p><b>为什么是「前缀白名单 + 否认前缀 + 敏感词正则」三层</b>：
 * 前缀是策略 —— 以后新增 {@code site.social.github} 不需要改代码；
 * 否认前缀与敏感词是纵深防御 —— 万一有人在 {@code site.} 下加了
 * {@code site.analytics.api_key}，它也不会顺着前缀一起漏出去。
 * 只留白名单一层的话，前者就是一次静默的凭据泄露。
 *
 * <p><b>永远不要</b>把 {@code system.}、{@code security.} 这类一级前缀放进白名单：
 * 开发库里就有 {@code system.storage.oss_secret_key}、{@code security.jwt.secret}。
 *
 * @author Ryu
 * @since 1.0.0
 */
public final class PublicConfigKeys {

    /**
     * 可以公开的键前缀
     *
     * <p>{@code system.maintenance.*} 暂时不在其中 —— 本项目没有公开前台消费它，
     * 真需要时再加，别为「以后可能要用」提前放开。
     */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "site.",
            "seo.",
            "article.display.",
            "article.publish.");

    /**
     * 显式否认的一级前缀
     *
     * <p>即使有人误把它们加进白名单，这里也先一步拦下 —— 两层判断顺序不能反。
     */
    private static final List<String> NEVER_PUBLIC_PREFIXES = List.of(
            "security.",
            "system.",
            "integration.",
            "ai.",
            "user.",
            "notification.");

    /** 键名里出现这些词一律不公开 */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(password|passwd|secret|token|credential|api[_-]?key|access[_-]?key|private[_-]?key).*");

    private PublicConfigKeys() {
    }

    /**
     * 判断某个配置键是否可以公开
     *
     * @param key 配置键
     * @return 可以公开返回 true
     */
    public static boolean isPublic(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (SENSITIVE.matcher(normalized).matches()) {
            return false;
        }
        if (NEVER_PUBLIC_PREFIXES.stream().anyMatch(normalized::startsWith)) {
            return false;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(normalized::startsWith);
    }
}
