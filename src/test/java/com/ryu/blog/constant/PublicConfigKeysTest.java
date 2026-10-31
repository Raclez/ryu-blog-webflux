package com.ryu.blog.constant;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公开配置白名单护栏
 *
 * <p>这是本功能里<b>唯一</b>一道决定「什么能匿名看到」的闸门，
 * 所以既验证白名单本身，也验证它不会被前缀绕过。
 *
 * @author ryu
 */
class PublicConfigKeysTest {

    @Test
    @DisplayName("站点/展示类前缀可以公开")
    void publicPrefixesAreAllowed() {
        assertTrue(PublicConfigKeys.isPublic("site.basic.name"));
        assertTrue(PublicConfigKeys.isPublic("site.basic.logo"));
        assertTrue(PublicConfigKeys.isPublic("seo.meta.author"));
        assertTrue(PublicConfigKeys.isPublic("article.display.page_size"));
        assertTrue(PublicConfigKeys.isPublic("article.publish.auto_save"));
    }

    @Test
    @DisplayName("敏感前缀永远不公开")
    void sensitivePrefixesAreNeverPublic() {
        assertFalse(PublicConfigKeys.isPublic("security.jwt.secret"));
        assertFalse(PublicConfigKeys.isPublic("system.storage.oss_secret_key"));
        assertFalse(PublicConfigKeys.isPublic("system.email.smtp_password"));
        assertFalse(PublicConfigKeys.isPublic("integration.github.client_secret"));
        assertFalse(PublicConfigKeys.isPublic("ai.provider.openai"));
        assertFalse(PublicConfigKeys.isPublic("user.theme.mode"));
        assertFalse(PublicConfigKeys.isPublic("notification.email.enable"));
    }

    @Test
    @DisplayName("前缀必须完整匹配，不做子串匹配")
    void prefixMatchingIsExact() {
        // "sitex." 不是 "site." 的前缀
        assertFalse(PublicConfigKeys.isPublic("sitex.basic.name"));
        // "seo" 这个键本身（没有点）也不在 "seo." 之下
        assertFalse(PublicConfigKeys.isPublic("seo"));
    }

    @Test
    @DisplayName("白名单前缀下的敏感词同样被拦下")
    void sensitiveWordWinsOverPublicPrefix() {
        // 将来有人在 site. 下加了带凭据的键，不能顺着前缀漏出去
        assertFalse(PublicConfigKeys.isPublic("site.analytics.api_key"));
        assertFalse(PublicConfigKeys.isPublic("site.basic.access_key"));
        assertFalse(PublicConfigKeys.isPublic("site.basic.apiKey"));
        assertFalse(PublicConfigKeys.isPublic("article.display.private_key"));
        assertFalse(PublicConfigKeys.isPublic("seo.meta.secret"));
    }

    @Test
    @DisplayName("空值不公开")
    void blankKeysAreNotPublic() {
        assertFalse(PublicConfigKeys.isPublic(null));
        assertFalse(PublicConfigKeys.isPublic(""));
        assertFalse(PublicConfigKeys.isPublic("   "));
    }

    @Test
    @DisplayName("大小写归一后再判定")
    void matchingIsCaseInsensitive() {
        assertTrue(PublicConfigKeys.isPublic("SITE.BASIC.NAME"));
        assertFalse(PublicConfigKeys.isPublic("SYSTEM.STORAGE.OSS_SECRET_KEY"));
    }

    @Property
    @Label("任何含敏感词的键都不可能公开")
    void anyKeyContainingSensitiveWordIsNotPublic(
            @ForAll @AlphaChars @StringLength(max = 30) String arbitrary) {
        String key = "site.basic." + arbitrary + "_password";
        assertFalse(PublicConfigKeys.isPublic(key),
                "含 password 的键不应公开: " + key);
    }
}
