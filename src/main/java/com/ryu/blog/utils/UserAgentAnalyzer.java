package com.ryu.blog.utils;

import lombok.extern.slf4j.Slf4j;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User-Agent解析工具类
 * 提供更准确的设备、浏览器和操作系统识别
 * 
 * @author ryu 475118582@qq.com
 */
@Slf4j
public class UserAgentAnalyzer {
    
    // 设备类型正则表达式
    private static final Pattern MOBILE_PATTERN = Pattern.compile(".*(iPhone|iPod|Android|Mobile|okhttp|MicroMessenger).*");
    private static final Pattern TABLET_PATTERN = Pattern.compile(".*(iPad|Tablet).*");
    
    // 浏览器类型正则表达式
    private static final Pattern CHROME_PATTERN = Pattern.compile(".*(Chrome|CriOS).*");
    private static final Pattern FIREFOX_PATTERN = Pattern.compile(".*(Firefox|FxiOS).*");
    private static final Pattern SAFARI_PATTERN = Pattern.compile(".*(Safari).*");
    private static final Pattern EDGE_PATTERN = Pattern.compile(".*(Edge|Edg|EdgiOS).*");
    private static final Pattern IE_PATTERN = Pattern.compile(".*(MSIE|Trident).*");
    private static final Pattern OPERA_PATTERN = Pattern.compile(".*(Opera|OPR|OPiOS).*");
    private static final Pattern WECHAT_PATTERN = Pattern.compile(".*(MicroMessenger).*");
    
    // 操作系统正则表达式
    private static final Pattern WINDOWS_PATTERN = Pattern.compile(".*(Windows).*");
    private static final Pattern MAC_PATTERN = Pattern.compile(".*(Mac OS).*");
    private static final Pattern IOS_PATTERN = Pattern.compile(".*(iPhone|iPad|iPod).*");
    private static final Pattern ANDROID_PATTERN = Pattern.compile(".*(Android).*");
    private static final Pattern LINUX_PATTERN = Pattern.compile(".*(Linux).*");
    
    /**
     * 解析User-Agent字符串，提取设备信息
     * 
     * @param userAgent User-Agent字符串
     * @return 设备信息对象
     */
    public static DeviceInfo parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return new DeviceInfo("Unknown", "Unknown", "Unknown");
        }
        
        String deviceType = getDeviceType(userAgent);
        String browserType = getBrowserType(userAgent);
        String osType = getOsType(userAgent);
        
        return new DeviceInfo(deviceType, browserType, osType);
    }
    
    /**
     * 获取设备类型
     * 
     * @param userAgent User-Agent字符串
     * @return 设备类型
     */
    public static String getDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        
        if (MOBILE_PATTERN.matcher(userAgent).matches()) {
            return "Mobile";
        } else if (TABLET_PATTERN.matcher(userAgent).matches()) {
            return "Tablet";
        } else {
            return "Desktop";
        }
    }
    
    /**
     * 获取浏览器类型
     * 
     * @param userAgent User-Agent字符串
     * @return 浏览器类型
     */
    public static String getBrowserType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        
        // 检查顺序很重要，因为有些浏览器会包含其他浏览器的标识
        if (WECHAT_PATTERN.matcher(userAgent).matches()) {
            return "WeChat";
        } else if (EDGE_PATTERN.matcher(userAgent).matches()) {
            return "Edge";
        } else if (OPERA_PATTERN.matcher(userAgent).matches()) {
            return "Opera";
        } else if (CHROME_PATTERN.matcher(userAgent).matches()) {
            return "Chrome";
        } else if (FIREFOX_PATTERN.matcher(userAgent).matches()) {
            return "Firefox";
        } else if (SAFARI_PATTERN.matcher(userAgent).matches()) {
            return "Safari";
        } else if (IE_PATTERN.matcher(userAgent).matches()) {
            return "Internet Explorer";
        } else {
            return "Other";
        }
    }
    
    /**
     * 获取操作系统类型
     * 
     * @param userAgent User-Agent字符串
     * @return 操作系统类型
     */
    public static String getOsType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        
        if (IOS_PATTERN.matcher(userAgent).matches()) {
            return "iOS";
        } else if (ANDROID_PATTERN.matcher(userAgent).matches()) {
            return "Android";
        } else if (MAC_PATTERN.matcher(userAgent).matches()) {
            return "macOS";
        } else if (WINDOWS_PATTERN.matcher(userAgent).matches()) {
            return "Windows";
        } else if (LINUX_PATTERN.matcher(userAgent).matches()) {
            return "Linux";
        } else {
            return "Other";
        }
    }
    
    /**
     * 格式化设备信息为可读字符串
     * 
     * @param userAgent User-Agent字符串
     * @return 格式化的设备信息
     */
    public static String formatDeviceInfo(String userAgent) {
        DeviceInfo info = parseUserAgent(userAgent);
        return String.format("%s / %s / %s", info.getDeviceType(), info.getBrowserType(), info.getOsType());
    }
    
    /**
     * 设备信息类
     */
    public static class DeviceInfo {
        private final String deviceType;
        private final String browserType;
        private final String osType;
        
        public DeviceInfo(String deviceType, String browserType, String osType) {
            this.deviceType = deviceType;
            this.browserType = browserType;
            this.osType = osType;
        }
        
        public String getDeviceType() {
            return deviceType;
        }
        
        public String getBrowserType() {
            return browserType;
        }
        
        public String getOsType() {
            return osType;
        }
        
        @Override
        public String toString() {
            return String.format("%s / %s / %s", deviceType, browserType, osType);
        }
    }
} 