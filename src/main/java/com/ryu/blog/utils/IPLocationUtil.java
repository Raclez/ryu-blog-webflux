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

/**
 * IP地址定位工具类
 * 
 * @author ryu 475118582@qq.com
 */
@Slf4j
public final class IPLocationUtil {

    private IPLocationUtil() {
    }
    
    /**
     * IP数据库文件路径
     */
    private static final String DB_PATH = "ip2region.xdb";
    
    /**
     * 搜索器
     *
     * <p>必须 volatile：{@link #close()} 会把引用置 null，而读线程在
     * {@link #getIpLocation(String)} 里先判空再调用 —— 不加 volatile 时
     * 读线程可能看到已关闭的实例（NPE / 使用已释放的缓冲区）。
     */
    private static volatile Searcher searcher = null;
    
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
        } catch (IOException e) {
            log.error("初始化IP2Region失败-IO异常: {}", e.getMessage(), e);
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
        } catch (IOException e) {
            log.warn("从ClassPath加载IP2Region数据库失败-IO异常: {}", e.getMessage());
            return null;
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
        } catch (IOException e) {
            log.error("从临时文件加载IP2Region数据库失败-IO异常: {}", e.getMessage());
            return null;
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
            // 取一次本地引用：避免判空之后 searcher 被 close() 置 null
            Searcher current = searcher;
            if (current != null) {
                return current.search(ip);
            }

            log.warn("IP2Region搜索器未初始化");
            return "未知";
        } catch (IOException e) {
            log.error("IP地址解析失败-IO异常: {}, {}", ip, e.getMessage());
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
            Searcher current = searcher;
            searcher = null;
            if (current != null) {
                current.close();
            }
            
            dbBytes = null;
            log.info("IP2Region资源释放成功");
        } catch (IOException e) {
            log.error("IP2Region资源释放失败-IO异常: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("IP2Region资源释放失败: {}", e.getMessage(), e);
        }
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