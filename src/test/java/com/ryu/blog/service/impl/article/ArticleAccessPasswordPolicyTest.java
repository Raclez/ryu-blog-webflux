package com.ryu.blog.service.impl.article;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章访问密码策略的单元测试
 *
 * <p>这套策略要同时吃下两种存法：<b>BCrypt 哈希</b>（2026-10-30 之后）
 * 与<b>历史明文</b>（2026-10-30 之前写进库的行）。
 * 两套存法的口令必须都能验过，且洗哈希不能改口令本身。
 *
 * @author ryu
 */
class ArticleAccessPasswordPolicyTest {

    @Test
    @DisplayName("编码：存的是哈希，不是明文")
    void encodeProducesHash() {
        String encoded = ArticleAccessPasswordPolicy.encode("secret");

        assertNotEquals("secret", encoded, "绝不能把明文写进库");
        assertTrue(encoded.startsWith("$2"), "BCrypt 哈希应以 $2 开头，实际: " + encoded);
        assertFalse(ArticleAccessPasswordPolicy.isPlaintext(encoded));
    }

    @Test
    @DisplayName("编码：同一个口令两次哈希的结果不同（盐是随机的）")
    void encodeUsesRandomSalt() {
        assertNotEquals(ArticleAccessPasswordPolicy.encode("secret"),
                ArticleAccessPasswordPolicy.encode("secret"));
    }

    @Test
    @DisplayName("编码：空白口令当成「没有访问密码」，返回 null")
    void encodeBlankReturnsNull() {
        assertNull(ArticleAccessPasswordPolicy.encode(null));
        assertNull(ArticleAccessPasswordPolicy.encode(""));
        assertNull(ArticleAccessPasswordPolicy.encode("   "));
    }

    @Test
    @DisplayName("校验：哈希能验过同一个口令，错的口令验不过")
    void matchesAgainstHash() {
        String encoded = ArticleAccessPasswordPolicy.encode("secret");

        assertTrue(ArticleAccessPasswordPolicy.matches("secret", encoded));
        assertFalse(ArticleAccessPasswordPolicy.matches("wrong", encoded));
        assertFalse(ArticleAccessPasswordPolicy.matches("", encoded));
        assertFalse(ArticleAccessPasswordPolicy.matches(null, encoded));
    }

    @Test
    @DisplayName("校验：历史明文也能验过（迁移前的老文章不能因此打不开）")
    void matchesLegacyPlaintext() {
        assertTrue(ArticleAccessPasswordPolicy.matches("secret", "secret"));
        assertFalse(ArticleAccessPasswordPolicy.matches("wrong", "secret"));
    }

    @Test
    @DisplayName("校验：库里没密码时，任何口令都过不了")
    void matchesWithoutStoredPassword() {
        assertFalse(ArticleAccessPasswordPolicy.matches("secret", null));
        assertFalse(ArticleAccessPasswordPolicy.matches("secret", ""));
        assertFalse(ArticleAccessPasswordPolicy.matches("secret", "   "));
    }

    @Test
    @DisplayName("识别：历史明文与哈希要能分清")
    void detectsPlaintext() {
        assertTrue(ArticleAccessPasswordPolicy.isPlaintext("secret"));
        assertTrue(ArticleAccessPasswordPolicy.isPlaintext("123456"));
        assertFalse(ArticleAccessPasswordPolicy.isPlaintext(
                ArticleAccessPasswordPolicy.encode("secret")));
        assertFalse(ArticleAccessPasswordPolicy.isPlaintext(null));
        assertFalse(ArticleAccessPasswordPolicy.isPlaintext(""));
    }

    @Test
    @DisplayName("洗哈希：历史明文被洗成哈希，但口令本身不变")
    void rehashKeepsThePasswordUsable() {
        String rehashed = ArticleAccessPasswordPolicy.rehashIfPlaintext("secret");

        assertNotEquals("secret", rehashed, "明文不能继续留在库里");
        assertTrue(rehashed.startsWith("$2"));
        assertTrue(ArticleAccessPasswordPolicy.matches("secret", rehashed),
                "洗成哈希之后，读者用原来的口令必须照样能打开文章");
    }

    @Test
    @DisplayName("洗哈希：已经是哈希的值原样返回（幂等）")
    void rehashIsIdempotent() {
        String encoded = ArticleAccessPasswordPolicy.encode("secret");

        assertEquals(encoded, ArticleAccessPasswordPolicy.rehashIfPlaintext(encoded));
    }

    @Test
    @DisplayName("洗哈希：空白值归成 null（文章本来就没有访问密码）")
    void rehashBlankReturnsNull() {
        assertNull(ArticleAccessPasswordPolicy.rehashIfPlaintext(null));
        assertNull(ArticleAccessPasswordPolicy.rehashIfPlaintext(""));
        assertNull(ArticleAccessPasswordPolicy.rehashIfPlaintext("   "));
    }
}
