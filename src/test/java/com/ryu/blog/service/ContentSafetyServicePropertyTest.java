package com.ryu.blog.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 内容安全服务属性测试
 * 
 * <p>测试属性9：内容安全一致性
 * 
 * @author Ryu
 * @since 1.0.0
 */
public class ContentSafetyServicePropertyTest {

    // 模拟的敏感词列表
    private static final Set<String> SENSITIVE_KEYWORDS = new HashSet<>(Arrays.asList(
        "violence", "hate", "illegal", "explicit", "dangerous"
    ));

    /**
     * 属性9：内容安全一致性
     * 
     * <p>对于任何生成的内容，如果包含不安全或不适当的内容，
     * 系统必须拒绝该生成结果并记录事件，不能让不安全内容通过。
     * 
     * <p>验证：需求11.1, 11.2
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void contentSafetyConsistency(
            @ForAll @StringLength(min = 50, max = 500) @AlphaChars String content) {
        
        // 检查内容安全性
        SafetyCheckResult result = checkContentSafety(content);
        
        // 验证：检查结果应该是确定的
        assert result != null
            : "安全检查结果不能为空";
        
        // 验证：如果内容不安全，必须被拒绝
        if (!result.isSafe) {
            assert result.rejectionReason != null && !result.rejectionReason.isEmpty()
                : "不安全内容必须提供拒绝原因";
            
            assert result.detectedIssues != null && !result.detectedIssues.isEmpty()
                : "不安全内容必须记录检测到的问题";
        }
        
        // 验证：如果内容安全，不应该有拒绝原因
        if (result.isSafe) {
            assert result.rejectionReason == null || result.rejectionReason.isEmpty()
                : "安全内容不应该有拒绝原因";
        }
    }

    /**
     * 验证敏感词检测的准确性
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void sensitiveKeywordDetectionAccuracy(
            @ForAll("contentWithSensitiveKeyword") String content) {
        
        SafetyCheckResult result = checkContentSafety(content);
        
        // 验证：包含敏感词的内容应该被检测出来
        boolean containsSensitiveWord = false;
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (content.toLowerCase().contains(keyword.toLowerCase())) {
                containsSensitiveWord = true;
                break;
            }
        }
        
        if (containsSensitiveWord) {
            assert !result.isSafe
                : "包含敏感词的内容应该被标记为不安全";
        }
    }

    /**
     * 验证安全检查的幂等性
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void safetyCheckIdempotency(
            @ForAll @StringLength(min = 50, max = 200) @AlphaChars String content) {
        
        // 多次检查相同内容
        SafetyCheckResult result1 = checkContentSafety(content);
        SafetyCheckResult result2 = checkContentSafety(content);
        SafetyCheckResult result3 = checkContentSafety(content);
        
        // 验证：相同内容的多次检查应该产生相同结果
        assert result1.isSafe == result2.isSafe
            : "相同内容的安全检查结果应该一致";
        
        assert result2.isSafe == result3.isSafe
            : "相同内容的安全检查结果应该一致";
        
        assert result1.isSafe == result3.isSafe
            : "相同内容的安全检查结果应该一致";
    }

    /**
     * 验证空内容的处理
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void emptyContentHandling() {
        String emptyContent = "";
        
        SafetyCheckResult result = checkContentSafety(emptyContent);
        
        // 验证：空内容应该被拒绝或标记为不安全
        assert !result.isSafe
            : "空内容应该被标记为不安全";
        
        assert result.rejectionReason != null
            : "空内容应该提供拒绝原因";
    }

    /**
     * 验证大小写不敏感的检测
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void caseInsensitiveDetection(@ForAll("mixedCaseSensitiveContent") String content) {
        
        SafetyCheckResult result = checkContentSafety(content);
        
        // 验证：无论大小写如何，敏感词都应该被检测到
        boolean containsSensitiveWord = false;
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (content.toLowerCase().contains(keyword.toLowerCase())) {
                containsSensitiveWord = true;
                break;
            }
        }
        
        if (containsSensitiveWord) {
            assert !result.isSafe
                : "敏感词检测应该不区分大小写";
        }
    }

    /**
     * 验证多个敏感词的检测
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void multipleSensitiveKeywordsDetection(
            @ForAll @IntRange(min = 1, max = 5) int keywordCount) {
        
        // 构造包含多个敏感词的内容
        StringBuilder content = new StringBuilder("This is a test content with ");
        List<String> keywords = Arrays.asList(SENSITIVE_KEYWORDS.toArray(new String[0]));
        
        for (int i = 0; i < Math.min(keywordCount, keywords.size()); i++) {
            content.append(keywords.get(i)).append(" ");
        }
        
        SafetyCheckResult result = checkContentSafety(content.toString());
        
        // 验证：包含多个敏感词的内容应该被检测出来
        assert !result.isSafe
            : "包含多个敏感词的内容应该被标记为不安全";
        
        // 验证：应该检测到所有敏感词
        assert result.detectedIssues.size() >= Math.min(keywordCount, keywords.size())
            : "应该检测到所有敏感词";
    }

    /**
     * 验证安全内容的通过
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void safeContentPassThrough(
            @ForAll @StringLength(min = 50, max = 200) @AlphaChars String safeContent) {
        
        // 确保内容不包含敏感词
        boolean containsSensitiveWord = false;
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (safeContent.toLowerCase().contains(keyword.toLowerCase())) {
                containsSensitiveWord = true;
                break;
            }
        }
        
        Assume.that(!containsSensitiveWord);
        
        SafetyCheckResult result = checkContentSafety(safeContent);
        
        // 验证：不包含敏感词的内容应该通过检查
        assert result.isSafe
            : "安全内容应该通过检查";
        
        assert result.detectedIssues.isEmpty()
            : "安全内容不应该有检测到的问题";
    }

    /**
     * 验证日志记录的完整性
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void safetyCheckLoggingCompleteness(
            @ForAll @StringLength(min = 50, max = 200) String content) {
        
        SafetyCheckResult result = checkContentSafety(content);
        
        // 验证：每次检查都应该有日志记录
        assert result.checkTimestamp > 0
            : "安全检查应该记录时间戳";
        
        // 验证：不安全内容应该记录详细信息
        if (!result.isSafe) {
            assert result.detectedIssues != null && !result.detectedIssues.isEmpty()
                : "不安全内容应该记录检测到的问题";
            
            assert result.rejectionReason != null && !result.rejectionReason.isEmpty()
                : "不安全内容应该记录拒绝原因";
        }
    }

    /**
     * 验证部分匹配的处理
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 9: 内容安全一致性")
    void partialMatchHandling(
            @ForAll @StringLength(min = 3, max = 10) @AlphaChars String prefix,
            @ForAll @StringLength(min = 3, max = 10) @AlphaChars String suffix) {
        
        // 构造包含敏感词的内容
        String sensitiveWord = SENSITIVE_KEYWORDS.iterator().next();
        String content = prefix + sensitiveWord + suffix;
        
        SafetyCheckResult result = checkContentSafety(content);
        
        // 验证：即使敏感词被其他文本包围，也应该被检测到
        assert !result.isSafe
            : "敏感词即使被其他文本包围也应该被检测到";
    }

    // 辅助方法和内部类

    /**
     * 检查内容安全性
     */
    private SafetyCheckResult checkContentSafety(String content) {
        SafetyCheckResult result = new SafetyCheckResult();
        result.checkTimestamp = System.currentTimeMillis();
        result.detectedIssues = new HashSet<>();
        
        // 空内容检查
        if (content == null || content.trim().isEmpty()) {
            result.isSafe = false;
            result.rejectionReason = "内容为空";
            result.detectedIssues.add("EMPTY_CONTENT");
            return result;
        }
        
        // 敏感词检查
        String lowerContent = content.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                result.isSafe = false;
                result.detectedIssues.add(keyword);
            }
        }
        
        if (!result.isSafe) {
            result.rejectionReason = "内容包含敏感词: " + result.detectedIssues;
        } else {
            result.isSafe = true;
        }
        
        return result;
    }

    /**
     * 生成包含敏感词的内容
     */
    @Provide
    Arbitrary<String> contentWithSensitiveKeyword() {
        return Arbitraries.of(SENSITIVE_KEYWORDS)
            .flatMap(keyword -> 
                Arbitraries.strings().alpha().ofMinLength(20).ofMaxLength(100)
                    .map(text -> text + " " + keyword + " " + text)
            );
    }

    /**
     * 生成混合大小写的敏感内容
     */
    @Provide
    Arbitrary<String> mixedCaseSensitiveContent() {
        return Arbitraries.of(SENSITIVE_KEYWORDS)
            .flatMap(keyword -> {
                // 随机改变大小写
                StringBuilder mixed = new StringBuilder();
                for (int i = 0; i < keyword.length(); i++) {
                    char c = keyword.charAt(i);
                    mixed.append(Math.random() > 0.5 ? Character.toUpperCase(c) : Character.toLowerCase(c));
                }
                return Arbitraries.strings().alpha().ofMinLength(20).ofMaxLength(100)
                    .map(text -> text + " " + mixed.toString() + " " + text);
            });
    }

    /**
     * 安全检查结果内部类
     */
    private static class SafetyCheckResult {
        boolean isSafe;
        String rejectionReason;
        Set<String> detectedIssues;
        long checkTimestamp;
    }
}
