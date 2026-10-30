package com.ryu.blog.utils;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * IP 地址工具类
 *
 * <p><b>代理头只在直连方是可信代理时才读取。</b>原先无条件相信
 * {@code X-Forwarded-For}：任何客户端自己带一个
 * {@code X-Forwarded-For: 1.2.3.4} 就能伪造 IP，浏览历史、地域统计、
 * 审计日志全部可被污染；如果后续拿它做限流，还会被直接绕过。
 *
 * <p>现在的规则：
 * <ul>
 *   <li>直连地址是回环 / 站点内网 / 链路本地地址 → 认为前面有一层反向代理，
 *       读取转发头，并取<b>最右侧</b>的有效地址（可信代理追加的那一跳）；</li>
 *   <li>否则一律使用 {@code remoteAddress}，忽略所有客户端可控头。</li>
 * </ul>
 *
 * <p>注意：如果反向代理不在本机/内网（例如跨主机部署且不共享网段），
 * 需要把代理地址加入可信列表 —— 当前实现采用「内网即可信」的保守近似。
 *
 * @author ryu 475118582@qq.com
 */
public final class IpUtil {

    private IpUtil() {
    }

    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
    private static final String UNKNOWN = "unknown";

    private static final List<String> IP_HEADERS = Arrays.asList(
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR",
            "X-Real-IP"
    );

    /**
     * 获取客户端 IP 地址
     *
     * @param exchange ServerWebExchange 对象
     * @return IP 地址（拿不到时返回 {@code unknown}）
     */
    public static String getClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        InetSocketAddress remote = request.getRemoteAddress();

        if (remote != null && remote.getAddress() != null
                && isTrustedProxy(remote.getAddress())) {
            for (String header : IP_HEADERS) {
                String value = request.getHeaders().getFirst(header);
                if (isValidIp(value)) {
                    return extractClientIp(value);
                }
            }
        }

        if (remote == null || remote.getAddress() == null) {
            return UNKNOWN;
        }

        String ip = remote.getAddress().getHostAddress();
        return LOCALHOST_IPV6.equals(ip) ? LOCALHOST_IPV4 : ip;
    }

    /**
     * 直连方是否是可信代理：本机、内网、链路本地。
     */
    private static boolean isTrustedProxy(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress();
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }

    /**
     * 从转发头中取客户端 IP。
     *
     * <p>转发头的格式是 {@code client, proxy1, proxy2}，由每一层代理<b>追加</b>。
     * 最左侧是客户端自己写的（可伪造），最右侧是离我们最近的可信代理追加的。
     * 这里从右往左取第一个有效地址，避免信任客户端伪造的第一段。
     */
    private static String extractClientIp(String headerValue) {
        String[] parts = headerValue.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (isValidIp(candidate)) {
                return candidate;
            }
        }
        return headerValue.trim();
    }
}
