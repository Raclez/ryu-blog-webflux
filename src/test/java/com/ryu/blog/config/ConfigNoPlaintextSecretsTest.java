package com.ryu.blog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>配置卫生护栏</b>：配置文件里不许出现凭据与地址的默认值。
 *
 * <p><b>它守的是什么</b>：Spring 的占位符代换支持 {@code ${VAR:默认值}} ——
 * 那个默认值一旦写进 {@code application*.yml} 就会<b>进版本库</b>。
 * 本项目已经因为它漏过一次：{@code application.yml} 里
 * {@code password: ${REDIS_PASSWORD:<真实口令>}} 的默认值是<b>真实的远程 Redis 口令</b>，
 * 而且从首个提交起就在 git 历史里。
 *
 * <p>所以这里的规则是硬的：
 * <ul>
 *   <li>凭据与地址类占位符<b>一律不带默认值</b>；</li>
 *   <li>本地开发需要兜底值 → 放 {@code application-dev.yml}（只在 dev profile 生效）；</li>
 *   <li>生产环境缺变量 → 直接启动失败，不静默兜底。</li>
 * </ul>
 *
 * <p>允许带默认值的只有一张白名单（见 {@link #ALLOWED_DEFAULTS}），
 * 每一个都有明确的理由，且都不是凭据。
 *
 * @author ryu
 */
class ConfigNoPlaintextSecretsTest {

    /** 配置文件目录（相对项目根）。 */
    private static final Path RESOURCES = Paths.get("src", "main", "resources");

    /**
     * 允许保留默认值的占位符 → 理由。
     *
     * <p>注意：这张表里的每一项都<b>不是凭据</b>。
     */
    private static final Map<String, String> ALLOWED_DEFAULTS = Map.of(
            // Spring AI 自动配置需要 api-key / endpoint 占位才能启动；
            // 真实配置来自数据库 t_sys_config。这里给的是假值「placeholder」。
            "OPENAI_API_KEY", "Spring AI 自动配置占位（真实 key 在数据库）",
            "AZURE_OPENAI_API_KEY", "Spring AI 自动配置占位（真实 key 在数据库）",
            "ANTHROPIC_API_KEY", "Spring AI 自动配置占位（真实 key 在数据库）",
            "OPENAI_BASE_URL", "官方公开端点，非秘密",
            "AZURE_OPENAI_ENDPOINT", "占位端点，非秘密",
            // ai profile 是被 spring.profiles.include 无条件引入的（每个 profile 都会加载），
            // 所以这两个功能开关的默认值放在 application-ai.yml —— 它们不是凭据也不是地址。
            "BLOG_AI_ENABLED", "AI 总开关（功能开关，非凭据）",
            "BLOG_AI_MAX_CONCURRENT", "AI 并发闸门（数值，非凭据）",
            // profile 自举：profile 决定 application-dev.yml 会不会被加载，
            // 没有默认值就形成循环依赖。
            "SPRING_PROFILES_ACTIVE", "profile 自举（dev 是本地兜底档）");

    /**
     * 不许出现字面量（必须是占位符）的配置键。
     *
     * <p>凭据类：写成字面量就等于把口令提交进仓库。
     */
    private static final Pattern CREDENTIAL_KEY = Pattern.compile(
            "(?im)^[ \\t]*(password|secret|secret-key|access-key|api-key|jwt-secret-key|token)"
                    + "[ \\t]*:[ \\t]*([^\\n]*)$");

    /**
     * 不许出现字面量（必须是占位符）的地址类配置键。
     */
    private static final Pattern ADDRESS_KEY = Pattern.compile(
            "(?im)^[ \\t]*(host|url|endpoint|address|bucket|region)[ \\t]*:[ \\t]*([^\\n]*)$");

    /** Spring AI 自动配置用的假值 —— 不是凭据。 */
    private static final Set<String> ALLOWED_LITERAL_VALUES = Set.of("placeholder");

    /** 匹配 {@code ${VAR}} 或 {@code ${VAR:默认值}}。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)(:([^}]*))?}");

    /** 真正需要外部提供的变量（.env / 容器 env / docker compose 都要给）。 */
    private static final List<String> REQUIRED_VARS = List.of(
            "DB_URL", "DB_USERNAME", "DB_PASSWORD",
            "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD", "REDIS_DATABASE",
            "JWT_SECRET", "SERVER_PORT", "UPLOAD_PATH");

    @Test
    @DisplayName("凭据与地址类占位符不许带默认值（除非在白名单里并写明理由）")
    void noUnexpectedDefaults() throws IOException {
        for (Path file : configFiles()) {
            if (isDevFallbackFile(file)) {
                // application-dev.yml 就是放本地兜底值的地方 —— 它带默认值是对的。
                continue;
            }
            for (String[] hit : scanPlaceholders(file)) {
                String name = hit[0];
                if (hit[1] == null) {
                    continue;
                }
                assertTrue(ALLOWED_DEFAULTS.containsKey(name),
                        file + " 里的 ${" + name + ":...} 带默认值，但不在白名单里。"
                                + "配置卫生规则：凭据与地址一律不带默认值；"
                                + "本地兜底值请放 application-dev.yml。");
            }
        }
    }

    @Test
    @DisplayName("主配置里每个无默认值的占位符，dev 档都要有对应的本地兜底值")
    void everyRequiredVarHasDevFallback() throws IOException {
        String dev = read(RESOURCES.resolve("application-dev.yml"));
        for (Path file : configFiles()) {
            if (isDevFallbackFile(file)) {
                continue;
            }
            for (String[] hit : scanPlaceholders(file)) {
                String name = hit[0];
                if (hit[1] != null) {
                    // 有默认值的走白名单，不要求 dev 兜底。
                    continue;
                }
                if (ALLOWED_DEFAULTS.containsKey(name)) {
                    continue;
                }
                assertTrue(dev.contains("${" + name + ":") || dev.contains("${" + name + "}"),
                        file + " 里的 ${" + name + "} 没有默认值，"
                                + "但 application-dev.yml 里也没有对应的本地兜底值 —— "
                                + "dev 环境会直接启动失败。三处要同步："
                                + "application.yml、application-dev.yml、.env.example");
            }
        }
    }

    @Test
    @DisplayName("凭据类配置项不许是字面量")
    void noLiteralCredentialValues() throws IOException {
        for (Path file : configFiles()) {
            assertLiteralFree(file, CREDENTIAL_KEY, "凭据");
        }
    }

    @Test
    @DisplayName("地址类配置项不许是字面量")
    void noLiteralAddressValues() throws IOException {
        for (Path file : configFiles()) {
            assertLiteralFree(file, ADDRESS_KEY, "地址");
        }
    }

    @Test
    @DisplayName("application-dev.yml 给每个需要的变量都留了本地兜底值")
    void devFileHasFallbackForEveryVar() throws IOException {
        String dev = read(RESOURCES.resolve("application-dev.yml"));
        for (String name : REQUIRED_VARS) {
            if ("JWT_SECRET".equals(name)) {
                // JWT_SECRET 故意没有**有效**兜底值：写成 ${JWT_SECRET:}（空默认值）——
                // 缺失时解析成空串，由 JwtSecretValidator 用中文提示拒绝启动。
                // 若这里出现非空的默认值，空密钥就等于「可伪造 admin token」。
                Matcher jwt = Pattern.compile("\\$\\{JWT_SECRET:([^}]*)\\}").matcher(dev);
                if (jwt.find()) {
                    assertTrue(jwt.group(1).isEmpty(),
                            "JWT_SECRET 不许有非空兜底值 —— 空密钥时 Sa-Token 会回退到"
                                    + "内置的公开默认密钥，任何人都能签发任意 userId 的 token 冒充 admin");
                }
                continue;
            }
            assertTrue(dev.contains("${" + name + ":"),
                    "application-dev.yml 里缺少 " + name + " 的本地兜底值（应为 ${" + name + ":...}）");
        }
    }

    @Test
    @DisplayName(".env.example 覆盖了所有需要外部提供的变量")
    void envExampleCoversRequiredVars() throws IOException {
        String example = read(Paths.get(".env.example"));
        for (String name : REQUIRED_VARS) {
            assertTrue(example.contains(name + "="),
                    ".env.example 里缺少 " + name + " —— 新增环境变量时三处要同步："
                            + "application.yml、application-dev.yml、.env.example");
        }
    }

    /**
     * 扫出文件里所有占位符 —— <b>跳过注释行</b>。
     *
     * <p>返回每个占位符的 {变量名, 默认值(没有则 null)}。
     */
    private List<String[]> scanPlaceholders(Path file) throws IOException {
        List<String[]> hits = new java.util.ArrayList<>();
        for (String line : read(file).split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                // 注释里出现 ${...} 只是说明文字，不是配置项。
                continue;
            }
            Matcher matcher = PLACEHOLDER.matcher(line);
            while (matcher.find()) {
                hits.add(new String[]{matcher.group(1), matcher.group(2)});
            }
        }
        return hits;
    }

    private void assertLiteralFree(Path file, Pattern pattern, String kind) throws IOException {
        List<String> violations = new java.util.ArrayList<>();
        Matcher matcher = pattern.matcher(read(file));
        while (matcher.find()) {
            String value = matcher.group(2).trim();
            if (value.isEmpty() || value.startsWith("${") || ALLOWED_LITERAL_VALUES.contains(value)) {
                continue;
            }
            violations.add(matcher.group(1));
        }
        assertTrue(violations.isEmpty(), file + " 里这些" + kind + "是写死的字面量：" + violations
                + "。应为 ${ENV_VAR} 占位符 —— 地址与口令只能来自环境变量，见 .env.example。");
    }

    private boolean isDevFallbackFile(Path file) {
        return "application-dev.yml".equals(file.getFileName().toString());
    }

    private List<Path> configFiles() throws IOException {
        try (var stream = Files.list(RESOURCES)) {
            return stream.filter(p -> p.getFileName().toString().startsWith("application"))
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        }
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
