package com.ryu.blog.utils;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown工具类
 * 提供Markdown解析、提取标题、摘要等功能
 *
 * @author ryu
 */
@Slf4j
public class MarkdownUtils {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();
    
    private MarkdownUtils() {
        // 工具类私有构造函数
    }
    
    /**
     * 将Markdown转换为HTML
     *
     * @param markdown Markdown内容
     * @return HTML内容
     */
    public static String renderHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        
        try {
            Node document = PARSER.parse(markdown);
            return HTML_RENDERER.render(document);
        } catch (Exception e) {
            log.error("Markdown转HTML失败: {}", e.getMessage(), e);
            return markdown;
        }
    }
    
    /**
     * 将Markdown转换为HTML（响应式）
     *
     * @param markdown Markdown内容
     * @return HTML内容的Mono
     */
    public static Mono<String> renderHtmlReactive(String markdown) {
        return Mono.fromCallable(() -> renderHtml(markdown));
    }
    
    /**
     * 从Markdown中提取标题
     * 优先提取H1标题，如果没有则提取第一个非空行
     *
     * @param markdown Markdown内容
     * @return 提取的标题
     */
    public static String extractTitle(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "未命名文章";
        }
        
        // 解析Markdown
        Node document = PARSER.parse(markdown);
        
        // 使用访问者模式查找第一个H1标题
        final String[] title = {null};
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                if (heading.getLevel() == 1 && title[0] == null) {
                    // 提取H1标题文本
                    StringBuilder sb = new StringBuilder();
                    heading.getFirstChild().accept(new AbstractVisitor() {
                        @Override
                        public void visit(Text text) {
                            sb.append(text.getLiteral());
                        }
                    });
                    title[0] = sb.toString().trim();
                }
                // 不继续访问子节点
                visitChildren(heading);
            }
        });
        
        // 如果找到了H1标题，直接返回
        if (title[0] != null && !title[0].isEmpty()) {
            return limitLength(title[0], 100);
        }
        
        // 没有找到H1标题，尝试使用正则表达式提取第一个非空行
        String[] lines = markdown.split("\\r?\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#") && !trimmedLine.startsWith("---")) {
                return limitLength(trimmedLine, 100);
            }
        }
        
        // 如果都没找到，返回默认标题
        return "未命名文章";
    }
    
    /**
     * 从Markdown中提取摘要
     * 提取第一个非空段落，排除代码块和标题
     *
     * @param markdown Markdown内容
     * @param maxLength 最大长度
     * @return 提取的摘要
     */
    public static String extractExcerpt(String markdown, int maxLength) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        
        // 先删除代码块
        String contentWithoutCodeBlocks = markdown.replaceAll("```[\\s\\S]*?```", "");
        
        // 解析Markdown
        Node document = PARSER.parse(contentWithoutCodeBlocks);
        
        // 使用访问者模式查找第一个段落
        final List<String> paragraphs = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Paragraph paragraph) {
                if (paragraphs.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    paragraph.accept(new AbstractVisitor() {
                        @Override
                        public void visit(Text text) {
                            sb.append(text.getLiteral());
                        }
                    });
                    String text = sb.toString().trim();
                    if (!text.isEmpty()) {
                        paragraphs.add(text);
                    }
                }
                // 不继续访问子节点
                super.visit(paragraph);
            }
        });
        
        // 如果找到了段落，返回
        if (!paragraphs.isEmpty()) {
            return limitLength(paragraphs.get(0), maxLength);
        }
        
        // 如果没有找到段落，使用正则表达式提取
        Pattern paragraphPattern = Pattern.compile("([\\p{L}\\p{N}][^\\n]+(?:\\n[^\\n]+)*)");
        Matcher paragraphMatcher = paragraphPattern.matcher(contentWithoutCodeBlocks);
        
        if (paragraphMatcher.find()) {
            return limitLength(paragraphMatcher.group(1).trim(), maxLength);
        }
        
        return "";
    }
    
    /**
     * 计算Markdown内容的阅读时间（分钟）
     * 基于平均阅读速度：中文300字/分钟，英文500词/分钟
     *
     * @param markdown Markdown内容
     * @return 预计阅读时间（分钟）
     */
    public static int calculateReadingTime(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return 1;
        }
        
        // 移除Markdown标记和代码块
        String plainText = markdown
                .replaceAll("```[\\s\\S]*?```", "") // 移除代码块
                .replaceAll("!?\\[.*?\\]\\(.*?\\)", "") // 移除链接和图片
                .replaceAll("#+ ", "") // 移除标题标记
                .replaceAll("\\*\\*|__", "") // 移除粗体标记
                .replaceAll("\\*|_", "") // 移除斜体标记
                .replaceAll("~~", "") // 移除删除线标记
                .replaceAll("`.*?`", "") // 移除行内代码
                .trim();
        
        // 计算中文字符数
        int chineseCharCount = 0;
        for (char c : plainText.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseCharCount++;
            }
        }
        
        // 计算英文单词数（简化处理，按空格分割）
        int englishWordCount = plainText.split("\\s+").length;
        
        // 计算阅读时间（分钟）
        double readingTime = (chineseCharCount / 300.0) + (englishWordCount / 500.0);
        
        // 最少返回1分钟
        return Math.max(1, (int) Math.ceil(readingTime));
    }
    
    /**
     * 计算Markdown内容的阅读时间（响应式）
     *
     * @param markdown Markdown内容
     * @return 预计阅读时间的Mono
     */
    public static Mono<Integer> calculateReadingTimeReactive(String markdown) {
        return Mono.fromCallable(() -> calculateReadingTime(markdown));
    }
    
    /**
     * 限制字符串长度，超出部分用省略号替代
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 处理后的文本
     */
    private static String limitLength(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
} 