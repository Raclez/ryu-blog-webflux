package com.ryu.blog.service.impl.file;

import java.util.Locale;
import java.util.Set;

/**
 * 上传内容的「主动内容」判定。
 *
 * <p><b>为什么需要它</b>：扩展名白名单（{@code FileUtils.isAllowedExtension}）
 * 只校验<b>文件名</b>，而落库的 MIME 是按<b>内容</b>嗅探出来的。两者之间没有任何校验，
 * 于是「内容是一段 HTML、名字叫 photo.png」可以完整走完白名单流程，
 * 最后以 {@code text/html} 入库 —— 名字合法，内容却是可执行的主动内容。
 *
 * <p>这类内容一旦被浏览器按自己的类型渲染，就是在站点域下执行脚本（存储型 XSS）。
 * 当前两个内联端点（{@code GET /files/public/**}、{@code GET /files/preview/**}）
 * 都下发了 {@code X-Content-Type-Options: nosniff} 与 {@code Content-Security-Policy: sandbox}，
 * 所以这条路现在打不通 —— 但那是<b>响应头</b>层的防线，一旦文件改由对象存储
 * 公开直读（响应头来自桶配置），这道防线就完全不存在了。
 *
 * <p>所以在<b>入库前</b>再判一次：嗅探出的 MIME 属于主动内容 → 拒绝上传。
 * 这是纵深防御，不是替代响应头。
 *
 * @author ryu
 */
public final class FileContentTypePolicy {

    /**
     * 能在浏览器里执行脚本、或能装载其它主动内容的 MIME。
     *
     * <ul>
     *   <li>{@code text/html} / {@code application/xhtml+xml} —— 直接执行内联脚本；</li>
     *   <li>{@code image/svg+xml} —— SVG 是 XML 文档，{@code <script>} 与事件属性都能跑；</li>
     *   <li>{@code application/javascript} / {@code text/javascript} —— 脚本本体；</li>
     *   <li>{@code application/xml} / {@code text/xml} —— XSLT / 外部实体的载体。</li>
     * </ul>
     */
    private static final Set<String> ACTIVE_CONTENT_TYPES = Set.of(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "application/javascript",
            "text/javascript",
            "application/x-javascript",
            "application/xml",
            "text/xml"
    );

    private FileContentTypePolicy() {
    }

    /**
     * 该 MIME 是否是主动内容。
     *
     * <p>只看 {@code type/subtype} 主段：真实世界里的 MIME 常带参数
     * （{@code text/html; charset=UTF-8}），整串比对会漏掉它们。
     *
     * @param mimeType 嗅探出的 MIME，可为 {@code null}
     * @return 属于主动内容返回 {@code true}
     */
    public static boolean isActiveContent(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        String base = mimeType;
        int semicolon = base.indexOf(';');
        if (semicolon >= 0) {
            base = base.substring(0, semicolon);
        }
        return ACTIVE_CONTENT_TYPES.contains(base.trim().toLowerCase(Locale.ROOT));
    }
}
