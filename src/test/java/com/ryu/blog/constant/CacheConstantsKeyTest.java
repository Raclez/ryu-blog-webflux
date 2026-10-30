package com.ryu.blog.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 缓存键常量的自洽性测试
 *
 * <p>背景：{@code CacheConstants} 里的 {@code *_KEY} 常量曾被写成
 * {@code "'entity:' + #key"}（自带引号和 SpEL），而调用处又套了一层引号，
 * 最终 SpEL 变成 {@code ''entity:' + #key' + #key} —— 语法错误，
 * 导致**每次读系统配置都抛 SpelParseException**，配置服务（含 AI Provider
 * 读取自己的 API Key）整个不可用。
 *
 * <p>这个测试把约定固化成可执行的断言：
 * <ol>
 *   <li>常量只放<b>前缀</b>，不带引号；</li>
 *   <li>按 {@code "'" + 前缀 + "'"} 拼出来的必须是合法 SpEL。</li>
 * </ol>
 */
class CacheConstantsKeyTest {

    @Test
    @DisplayName("缓存键常量不能自带引号（会与调用处的引号叠加成非法 SpEL）")
    void keyConstantsAreBarePrefixes() throws IllegalAccessException {
        for (Field field : keyConstantFields()) {
            String value = (String) field.get(null);
            assertFalse(value.startsWith("'"),
                    () -> field.getName() + " 自带引号，与调用处的引号叠加后 SpEL 非法：" + value);
        }
    }

    @Test
    @DisplayName("按 '前缀' 拼出的缓存键必须是合法 SpEL")
    void keyExpressionsAreParsable() throws IllegalAccessException {
        SpelExpressionParser parser = new SpelExpressionParser();

        for (Field field : keyConstantFields()) {
            String value = (String) field.get(null);
            String expression = "'" + value + "'";
            assertDoesNotThrow(() -> parser.parseExpression(expression),
                    () -> field.getName() + " 拼出的 SpEL 非法：" + expression);
        }
    }

    /**
     * 所有对外暴露的字符串型缓存键常量
     */
    private Field[] keyConstantFields() {
        return java.util.Arrays.stream(CacheConstants.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> field.getType() == String.class)
                .filter(field -> field.getName().endsWith("_KEY"))
                .toArray(Field[]::new);
    }
}
