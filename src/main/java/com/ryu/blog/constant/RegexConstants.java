package com.ryu.blog.constant;

/**
 * 正则表达式常量
 * 
 * @author ryu
 */
public class RegexConstants {
    
    /** 用户名正则表达式：字母开头，允许5-20字节，允许字母数字下划线 */
    public static final String USERNAME_REGEX = "^[a-zA-Z][a-zA-Z0-9_]{4,19}$";
    
    /** 密码正则表达式：必须包含大小写字母和数字，可以包含特殊字符，长度8-20 */
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d\\W]{8,20}$";
    
    /** 简单密码正则表达式：只要求6-20位字符 */
    public static final String SIMPLE_PASSWORD_REGEX = "^[a-zA-Z0-9\\W]{6,20}$";
    
    /** 邮箱正则表达式 */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
    
    /** 手机号正则表达式（中国大陆） */
    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    
    /** 身份证号正则表达式（18位） */
    public static final String ID_CARD_REGEX = "^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$";
    
    /** URL正则表达式 */
    public static final String URL_REGEX = "^(https?|ftp)://([a-zA-Z0-9.-]+(:[a-zA-Z0-9.&%$-]+)*@)*((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?)(\\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}|([a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+\\.(com|edu|gov|int|mil|net|org|biz|arpa|info|name|pro|aero|coop|museum|[a-zA-Z]{2}))(:[0-9]+)*(/($|[a-zA-Z0-9.,?'\\\\+&%$#=~_-]+))*$";
    
    /** IP地址正则表达式 */
    public static final String IP_REGEX = "^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\.(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\.(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\.(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$";
    
    /** MAC地址正则表达式 */
    public static final String MAC_REGEX = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
    
    /** 邮政编码正则表达式 */
    public static final String ZIPCODE_REGEX = "^[1-9]\\d{5}$";
    
    /** 中文字符正则表达式 */
    public static final String CHINESE_REGEX = "^[\\u4e00-\\u9fa5]+$";
    
    /** 日期正则表达式（yyyy-MM-dd） */
    public static final String DATE_REGEX = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$";
    
    /** 时间正则表达式（HH:mm:ss） */
    public static final String TIME_REGEX = "^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$";
    
    /** 日期时间正则表达式（yyyy-MM-dd HH:mm:ss） */
    public static final String DATE_TIME_REGEX = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])\\s([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$";
    
    /** 数字正则表达式（正整数、负整数、正小数、负小数） */
    public static final String NUMBER_REGEX = "^-?\\d+(\\.\\d+)?$";
    
    /** 整数正则表达式 */
    public static final String INTEGER_REGEX = "^-?\\d+$";
    
    /** 正整数正则表达式 */
    public static final String POSITIVE_INTEGER_REGEX = "^[1-9]\\d*$";
    
    /** 小数正则表达式 */
    public static final String DECIMAL_REGEX = "^-?\\d+\\.\\d+$";
    
    /** 正小数正则表达式 */
    public static final String POSITIVE_DECIMAL_REGEX = "^[1-9]\\d*\\.\\d+|0\\.\\d*[1-9]\\d*$";
    
    /** 非负整数正则表达式（正整数+0） */
    public static final String NON_NEGATIVE_INTEGER_REGEX = "^\\d+$";
    
    /** 非负小数正则表达式（正小数+0） */
    public static final String NON_NEGATIVE_DECIMAL_REGEX = "^\\d+(\\.\\d+)?$";
    
    /** 16进制颜色代码正则表达式 */
    public static final String HEX_COLOR_REGEX = "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$";
    
    /** 图片文件名正则表达式 */
    public static final String IMAGE_FILE_REGEX = "^.+\\.(jpg|jpeg|png|gif|bmp)$";
    
    /** 视频文件名正则表达式 */
    public static final String VIDEO_FILE_REGEX = "^.+\\.(mp4|avi|rmvb|rm|flv|mkv|wmv|mov)$";
    
    /** 音频文件名正则表达式 */
    public static final String AUDIO_FILE_REGEX = "^.+\\.(mp3|wav|wma|ogg|ape|flac)$";
    
    /** 文档文件名正则表达式 */
    public static final String DOCUMENT_FILE_REGEX = "^.+\\.(doc|docx|xls|xlsx|ppt|pptx|pdf|txt|md)$";
    
    /** 压缩文件名正则表达式 */
    public static final String COMPRESSED_FILE_REGEX = "^.+\\.(zip|rar|7z|gz|tar)$";
    
    /** HTML标签正则表达式 */
    public static final String HTML_TAG_REGEX = "<[^>]+>";
    
    /** SQL注入检测正则表达式 */
    public static final String SQL_INJECTION_REGEX = "(?:')|(?:--)|(/\\*(?:.|[\\n\\r])*?\\*/)|(\\b(select|update|and|or|delete|insert|trancate|char|into|substr|ascii|declare|exec|count|master|into|drop|execute)\\b)";
} 