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
public final class MarkdownUtils {

    private static final Parser PARSER = Parser.builder().build();

    /**
     * HTML 渲染器 —— <b>安全模式</b>。
     *
     * <p>commonmark 的 {@code HtmlRenderer} 两个安全开关<b>默认都是关的</b>
     * （{@code escapeHtml=false}、{@code sanitizeUrls=false}，已用 javap 反编译
     * {@code HtmlRenderer$Builder} 的构造函数字节码确认）。默认值下：
     *
     * <pre>{@code
     * 输入: Hello <script>alert(1)</script>  [x](javascript:alert(3))
     * 输出: <p>Hello <script>alert(1)</script></p>
     *       <p><a href="javascript:alert(3)">x</a></p>
     * }</pre>
     *
     * <p>也就是<b>原文里的裸 HTML 与 {@code javascript:} 伪协议会原样进入渲染结果</b>。
     * 文章正文是用户创作内容，渲染后会被前台直接插入 DOM ——
     * 这是一条完整的<b>存储型 XSS</b> 通路。
     *
     * <p>打开两个开关后：
     *
     * <pre>{@code
     * 输出: <p>Hello &lt;script&gt;alert(1)&lt;/script&gt;</p>
     *       <p><a rel="nofollow" href="">x</a></p>
     * }</pre>
     *
     * <p>代价是「作者在 Markdown 里内嵌 HTML」不再生效（会被转义成文本显示）。
     * 对博客正文而言这是可接受的取舍：正文渲染的默认路径应该是安全的。
     */
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

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
            // fail-closed：渲染失败时**不能**把原文原样吐出去 ——
            // 调用方（ContentServiceImpl#markdownToHtml）拿到的字符串会被前台
            // 直接塞进 DOM，原文里的裸 HTML 就会变成可执行脚本。
            // 这里退化成「转义后的纯文本」：内容还在，但不可执行。
            log.error("Markdown转HTML失败: {}", e.getMessage(), e);
            return escapeHtml(markdown);
        }
    }

    /**
     * HTML 转义（用于渲染失败时的降级输出）
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
                    // 空标题（只有 "#"）没有子节点；不判空会在保存文章时 NPE
                    Node firstChild = heading.getFirstChild();
                    if (firstChild != null) {
                        StringBuilder sb = new StringBuilder();
                        firstChild.accept(new AbstractVisitor() {
                            @Override
                            public void visit(Text text) {
                                sb.append(text.getLiteral());
                            }
                        });
                        title[0] = sb.toString().trim();
                    }
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
        
        // 计算英文单词数：先把汉字替换成空格，再按空白分词。
        // 原先直接 split，中文会被当成「一个英文单词」，空字符串也会得到 1。
        String nonHanText = plainText.replaceAll("\\p{IsHan}", " ").trim();
        int englishWordCount = nonHanText.isEmpty() ? 0 : nonHanText.split("\\s+").length;
        
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