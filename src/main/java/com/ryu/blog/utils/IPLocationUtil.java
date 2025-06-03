package com.ryu.blog.utils;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.server.ServerWebExchange;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * IP地址定位工具类
 * 
 * @author ryu 475118582@qq.com
 */
@Slf4j
public class IPLocationUtil {
    
    /**
     * IP数据库文件路径
     */
    private static final String DB_PATH = "ip2region.xdb";
    
    /**
     * 搜索器
     */
    private static Searcher searcher = null;
    
    /**
     * 数据库二进制内容缓存
     */
    private static byte[] dbBytes = null;
    
    /**
     * 初始化IP数据库，按优先级尝试不同加载方式
     */
    static {
        try {
            // 1. 尝试从ClassPath加载
            dbBytes = loadDbFromClassPath();
            
            // 2. 如果ClassPath加载失败，尝试从临时文件加载
            if (dbBytes == null) {
                dbBytes = loadDbFromTempFile();
            }
            
            // 初始化搜索器
            if (dbBytes != null) {
                searcher = Searcher.newWithBuffer(dbBytes);
                log.info("IP2Region搜索器初始化成功");
            }
        } catch (Exception e) {
            log.error("初始化IP2Region失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从ClassPath加载数据库文件
     */
    private static byte[] loadDbFromClassPath() {
        try {
            ClassPathResource resource = new ClassPathResource(DB_PATH);
            InputStream inputStream = resource.getInputStream();
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            
            log.info("从ClassPath加载IP2Region数据库成功");
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.warn("从ClassPath加载IP2Region数据库失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从临时文件加载数据库文件
     */
    private static byte[] loadDbFromTempFile() {
        File tempFile = null;
        
        try {
            ClassPathResource resource = new ClassPathResource(DB_PATH);
            tempFile = File.createTempFile("ip2region", ".xdb");
            FileCopyUtils.copy(resource.getInputStream(), new FileOutputStream(tempFile));
            
            // 从临时文件加载到内存
            byte[] bytes = Searcher.loadContentFromFile(tempFile.getPath());
            log.info("从临时文件加载IP2Region数据库成功");
            return bytes;
        } catch (Exception e) {
            log.error("从临时文件加载IP2Region数据库失败: {}", e.getMessage());
            return null;
        } finally {
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    tempFile.deleteOnExit();
                }
            }
        }
    }
    
    /**
     * 根据IP获取地理位置
     * 
     * @param ip IP地址
     * @return 地理位置，格式：国家|区域|省份|城市|ISP
     */
    public static String getIpLocation(String ip) {
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip) || 
            "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "本地局域网";
        }
        
        try {
            if (searcher != null) {
                return searcher.search(ip);
            }
            
            log.warn("IP2Region搜索器未初始化");
            return "未知";
        } catch (Exception e) {
            log.error("IP地址解析失败: {}, {}", ip, e.getMessage());
            return "未知";
        }
    }
    
    /**
     * 获取客户端IP地址的地理位置
     * 
     * @param exchange ServerWebExchange对象
     * @return 地理位置
     */
    public static String getClientIpLocation(ServerWebExchange exchange) {
        String ip = IpUtil.getClientIp(exchange);
        return getIpLocation(ip);
    }
    
    /**
     * 格式化地理位置信息
     * 
     * @param location 原始地理位置信息，格式：国家|区域|省份|城市|ISP
     * @return 格式化后的地理位置信息
     */
    public static IPLocationInfo parseLocation(String location) {
        if (location == null || location.isEmpty() || "未知".equals(location) || "本地局域网".equals(location)) {
            return new IPLocationInfo("", "", "", "", "");
        }
        
        String[] parts = location.split("\\|");
        String country = parts.length > 0 ? parts[0].trim() : "";
        String region = parts.length > 1 ? parts[1].trim() : "";
        String province = parts.length > 2 ? parts[2].trim() : "";
        String city = parts.length > 3 ? parts[3].trim() : "";
        String isp = parts.length > 4 ? parts[4].trim() : "";
        
        return new IPLocationInfo(country, region, province, city, isp);
    }
    
    /**
     * 释放资源
     */
    public static void close() {
        try {
            if (searcher != null) {
                searcher.close();
                searcher = null;
            }
            
            dbBytes = null;
            log.info("IP2Region资源释放成功");
        } catch (Exception e) {
            log.error("IP2Region资源释放失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 测试IP解析功能
     * 
     * @param args 参数
     * @throws Exception 异常
     */
    public static void main(String[] args) throws Exception {
        // 测试IP地址
        String[] testIps = {
            "127.0.0.1",
            "114.114.114.114",  // 114DNS
            "223.5.5.5",        // 阿里DNS
            "8.8.8.8",          // Google DNS
            "183.232.231.172",  // 腾讯
            "101.226.4.6"       // 上海电信
        };
        
        // 预热
        long startTime = System.nanoTime();
        for (String ip : testIps) {
            getIpLocation(ip);
        }
        long endTime = System.nanoTime();
        System.out.printf("预热耗时: %.5f 毫秒\n", (endTime - startTime) / 1000000.0);
        
        // 测试性能
        int total = 10000;
        startTime = System.nanoTime();
        for (int i = 0; i < total; i++) {
            getIpLocation(testIps[i % testIps.length]);
        }
        endTime = System.nanoTime();
        System.out.printf("平均查询耗时: %.5f 毫秒\n", (endTime - startTime) / 1000000.0 / total);
        
        // 输出地理位置信息
        for (String ip : testIps) {
            String location = getIpLocation(ip);
            IPLocationInfo info = parseLocation(location);
            System.out.printf("IP: %s\n位置: %s\n解析: %s\n\n", ip, location, info);
        }
        
        // 释放资源
        close();
        TimeUnit.SECONDS.sleep(1);
    }
    
    /**
     * IP地理位置信息类
     */
    public static class IPLocationInfo {
        private final String country;  // 国家
        private final String region;   // 区域
        private final String province; // 省份
        private final String city;     // 城市
        private final String isp;      // ISP服务商
        
        public IPLocationInfo(String country, String region, String province, String city, String isp) {
            this.country = country;
            this.region = region;
            this.province = province;
            this.city = city;
            this.isp = isp;
        }
        
        public String getCountry() {
            return country;
        }
        
        public String getRegion() {
            return region;
        }
        
        public String getProvince() {
            return province;
        }
        
        public String getCity() {
            return city;
        }
        
        public String getIsp() {
            return isp;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (!country.isEmpty() && !"0".equals(country)) {
                sb.append(country);
            }
            if (!province.isEmpty() && !"0".equals(province)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(province);
            }
            if (!city.isEmpty() && !"0".equals(city)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(city);
            }
            if (!isp.isEmpty() && !"0".equals(isp)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(isp);
            }
            return sb.toString();
        }
    }
} 