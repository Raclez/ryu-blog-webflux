package com.ryu.blog.utils;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * IP地址工具类
 * 
 * @author ryu 475118582@qq.com
 */
public class IpUtil {
    
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
     * 获取客户端IP地址
     * 
     * @param exchange ServerWebExchange对象
     * @return IP地址
     */
    public static String getClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 从请求头中获取IP
        for (String header : IP_HEADERS) {
            String ip = request.getHeaders().getFirst(header);
            if (isValidIp(ip)) {
                // 多次反向代理后会有多个IP值，第一个为真实IP
                return extractFirstIp(ip);
            }
        }
        
        // 从远程地址获取IP
        if (request.getRemoteAddress() != null) {
            String ip = request.getRemoteAddress().getAddress().getHostAddress();
            if (LOCALHOST_IPV6.equals(ip)) {
                // 如果是IPv6的本地地址，则返回IPv4的本地地址
                return LOCALHOST_IPV4;
            }
            return ip;
        }
        
        return UNKNOWN;
    }
    
    /**
     * 检查IP是否有效
     * 
     * @param ip IP地址
     * @return 是否有效
     */
    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }
    
    /**
     * 提取多个IP中的第一个有效IP
     * 
     * @param ip 可能包含多个IP的字符串，以逗号分隔
     * @return 第一个有效IP
     */
    private static String extractFirstIp(String ip) {
        if (ip.contains(",")) {
            // 取第一个有效IP
            String[] ips = ip.split(",");
            for (String subIp : ips) {
                if (isValidIp(subIp)) {
                    return subIp.trim();
                }
            }
        }
        return ip.trim();
    }
    
    /**
     * 获取本机IP地址
     * 
     * @return 本机IP地址
     */
    public static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return LOCALHOST_IPV4;
        }
    }
    
    /**
     * 判断IP是否为内网IP
     * 
     * @param ip IP地址
     * @return 是否为内网IP
     */
    public static boolean isInternalIp(String ip) {
        if (LOCALHOST_IPV4.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
            return true;
        }
        
        try {
            byte[] addr = InetAddress.getByName(ip).getAddress();
            
            // 10.x.x.x/8
            if (addr[0] == (byte) 10) {
                return true;
            }
            
            // 172.16.x.x/12
            if (addr[0] == (byte) 172 && (addr[1] & 0xF0) == 16) {
                return true;
            }
            
            // 192.168.x.x/16
            if (addr[0] == (byte) 192 && addr[1] == (byte) 168) {
                return true;
            }
            
            // 169.254.x.x/16
            if (addr[0] == (byte) 169 && addr[1] == (byte) 254) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        
        return false;
    }
} 