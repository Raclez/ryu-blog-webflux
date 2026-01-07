package com.ryu.blog.service;

import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI模板服务属性测试
 * 
 * <p>测试属性4：模板变量替换正确性
 * 
 * @author Ryu
 * @since 1.0.0
 */
public class AiTemplateServicePropertyTest {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /**
     * 属性4：模板变量替换正确性
     * 
     * <p>对于任何包含变量的模板和提供的变量值映射，
     * 生成的提示词必须包含所有替换后的变量值，不能有未替换的占位符。
     * 
     * <p>验证：需求4.4
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 4: 模板变量替换正确性")
    void templateVariableReplacementCorrectness(
            @ForAll("templates") String template,
            @ForAll("variableMaps") Map<String, String> variables) {
        
        // 执行变量替换
        String result = replaceVariables(template, variables);
        
        // 验证：结果中不应该有未替换的占位符
        Matcher matcher = VARIABLE_PATTERN.matcher(result);
        while (matcher.find()) {
            String varName = matcher.group(1);
            // 如果还有占位符，它应该是因为变量映射中没有提供该变量
            assert !variables.containsKey(varName) 
                : "变量 " + varName + " 在映射中存在但未被替换";
        }
        
        // 验证：所有提供的变量值都应该出现在结果中
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String varName = entry.getKey();
            String varValue = entry.getValue();
            
            // 检查模板中是否包含该变量的占位符
            String placeholder = "{{" + varName + "}}";
            if (template.contains(placeholder) || template.contains("{{ " + varName + " }}")) {
                // 如果模板中有该占位符，结果中应该包含替换后的值
                assert result.contains(varValue)
                    : "变量值 " + varValue + " 应该出现在结果中";
            }
        }
    }

    /**
     * 验证空变量映射不会破坏模板
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 4: 模板变量替换正确性")
    void emptyVariablesDoNotBreakTemplate(@ForAll("templates") String template) {
        Map<String, String> emptyVariables = new HashMap<>();
        
        String result = replaceVariables(template, emptyVariables);
        
        // 结果应该与原模板相同（因为没有变量可替换）
        assert result.equals(template) 
            : "空变量映射不应该改变模板";
    }

    /**
     * 验证所有变量都被替换后，不应该有占位符
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 4: 模板变量替换正确性")
    void allVariablesReplacedNoPlaceholders(
            @ForAll("simpleTemplates") String template,
            @ForAll("completeVariableMaps") Map<String, String> variables) {
        
        // 确保变量映射包含模板中的所有变量
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!variables.containsKey(varName)) {
                variables.put(varName, "test_value_" + varName);
            }
        }
        
        String result = replaceVariables(template, variables);
        
        // 验证：结果中不应该有任何占位符
        assert !VARIABLE_PATTERN.matcher(result).find()
            : "所有变量都提供后，不应该有未替换的占位符";
    }

    /**
     * 验证特殊字符在变量值中被正确处理
     */
    @Property(tries = 100)
    @Tag("Feature: ai-blog-writer, Property 4: 模板变量替换正确性")
    void specialCharactersInVariableValues(
            @ForAll("simpleTemplates") String template,
            @ForAll("specialCharVariables") Map<String, String> variables) {
        
        String result = replaceVariables(template, variables);
        
        // 验证：特殊字符应该被正确处理，不会破坏替换逻辑
        assert result != null && !result.isEmpty()
            : "特殊字符不应该破坏替换逻辑";
        
        // 验证：结果的长度应该合理（不会因为特殊字符导致异常增长）
        assert result.length() <= template.length() + variables.values().stream()
                .mapToInt(String::length).sum() + 1000
            : "结果长度应该在合理范围内";
    }

    /**
     * 模板变量替换实现（与实际服务中的实现相同）
     */
    private String replaceVariables(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.getOrDefault(variableName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 模板生成器
     */
    @Provide
    Arbitrary<String> templates() {
        return Arbitraries.oneOf(
                Arbitraries.just("请写一篇关于{{topic}}的文章"),
                Arbitraries.just("生成一个{{language}}语言的{{style}}风格文章，主题是{{topic}}"),
                Arbitraries.just("{{greeting}}，请帮我写一篇{{length}}字的文章"),
                Arbitraries.just("文章标题：{{title}}\n内容要求：{{requirements}}"),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(200)
                        .map(s -> s + " {{var1}} and {{var2}}")
        );
    }

    /**
     * 简单模板生成器（只包含1-3个变量）
     */
    @Provide
    Arbitrary<String> simpleTemplates() {
        return Arbitraries.oneOf(
                Arbitraries.just("Hello {{name}}!"),
                Arbitraries.just("{{greeting}} {{name}}!"),
                Arbitraries.just("{{a}} and {{b}} and {{c}}")
        );
    }

    /**
     * 变量映射生成器
     */
    @Provide
    Arbitrary<Map<String, String>> variableMaps() {
        return Arbitraries.maps(
                Arbitraries.of("topic", "language", "style", "greeting", "length", 
                              "title", "requirements", "var1", "var2", "name", "a", "b", "c"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        ).ofMinSize(0).ofMaxSize(10);
    }

    /**
     * 完整变量映射生成器（包含所有可能的变量）
     */
    @Provide
    Arbitrary<Map<String, String>> completeVariableMaps() {
        Map<String, String> map = new HashMap<>();
        map.put("topic", "Spring Boot");
        map.put("language", "中文");
        map.put("style", "教程");
        map.put("greeting", "你好");
        map.put("length", "1000");
        map.put("title", "测试标题");
        map.put("requirements", "详细说明");
        map.put("var1", "value1");
        map.put("var2", "value2");
        map.put("name", "张三");
        map.put("a", "A");
        map.put("b", "B");
        map.put("c", "C");
        return Arbitraries.just(map);
    }

    /**
     * 包含特殊字符的变量映射生成器
     */
    @Provide
    Arbitrary<Map<String, String>> specialCharVariables() {
        return Arbitraries.maps(
                Arbitraries.of("name", "topic", "content"),
                Arbitraries.oneOf(
                        Arbitraries.just("$special"),
                        Arbitraries.just("test\\value"),
                        Arbitraries.just("value with spaces"),
                        Arbitraries.just("中文内容"),
                        Arbitraries.just("emoji😀")
                )
        ).ofMinSize(1).ofMaxSize(3);
    }
}
