package com.ryu.blog.utils;

import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.ChineseCaptcha;
import com.wf.captcha.GifCaptcha;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;

/**
 * 验证码工具类
 * 
 * @author ryu 475118582@qq.com
 */
@Slf4j
public class CaptchaUtil {
    
    /**
     * 验证码类型
     */
    public enum CaptchaType {
        /**
         * 普通类型
         */
        SPEC,
        /**
         * GIF类型
         */
        GIF,
        /**
         * 中文类型
         */
        CHINESE,
        /**
         * 算术类型
         */
        ARITHMETIC
    }
    
    /**
     * 获取验证码
     * 
     * @param width 宽度
     * @param height 高度
     * @param length 长度
     * @param type 类型
     * @param font 字体
     * @return 验证码
     */
    public static Captcha createCaptcha(int width, int height, int length, CaptchaType type, Font font) {
        Captcha captcha;
        switch (type) {
            case SPEC:
                captcha = new SpecCaptcha(width, height, length);
                break;
            case GIF:
                captcha = new GifCaptcha(width, height, length);
                break;
            case CHINESE:
                captcha = new ChineseCaptcha(width, height, length);
                break;
            case ARITHMETIC:
                captcha = new ArithmeticCaptcha(width, height);
                ((ArithmeticCaptcha) captcha).setLen(length);
                break;
            default:
                captcha = new SpecCaptcha(width, height, length);
        }
        
        // 设置字体
        if (font != null) {
            captcha.setFont(font);
        }
        
        return captcha;
    }
    
    /**
     * 获取验证码（默认宽高）
     * 
     * @param length 长度
     * @param type 类型
     * @return 验证码
     */
    public static Captcha createCaptcha(int length, CaptchaType type) {
        return createCaptcha(130, 48, length, type, null);
    }
    
    /**
     * 获取验证码（默认参数）
     * 
     * @return 验证码
     */
    public static Captcha createCaptcha() {
        return createCaptcha(5, CaptchaType.SPEC);
    }
    
    /**
     * 获取算术验证码
     * 
     * @param width 宽度
     * @param height 高度
     * @param digitCount 位数（1-3）
     * @return 算术验证码
     */
    public static ArithmeticCaptcha createArithmeticCaptcha(int width, int height, int digitCount) {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(width, height);
        captcha.setLen(digitCount);
        return captcha;
    }
    
    /**
     * 获取算术验证码（默认参数）
     * 
     * @return 算术验证码
     */
    public static ArithmeticCaptcha createArithmeticCaptcha() {
        return createArithmeticCaptcha(130, 48, 2);
    }
    
    /**
     * 获取中文验证码
     * 
     * @param width 宽度
     * @param height 高度
     * @param length 长度
     * @return 中文验证码
     */
    public static ChineseCaptcha createChineseCaptcha(int width, int height, int length) {
        return new ChineseCaptcha(width, height, length);
    }
    
    /**
     * 获取中文验证码（默认参数）
     * 
     * @return 中文验证码
     */
    public static ChineseCaptcha createChineseCaptcha() {
        return createChineseCaptcha(130, 48, 4);
    }
    
    /**
     * 获取GIF验证码
     * 
     * @param width 宽度
     * @param height 高度
     * @param length 长度
     * @return GIF验证码
     */
    public static GifCaptcha createGifCaptcha(int width, int height, int length) {
        return new GifCaptcha(width, height, length);
    }
    
    /**
     * 获取GIF验证码（默认参数）
     * 
     * @return GIF验证码
     */
    public static GifCaptcha createGifCaptcha() {
        return createGifCaptcha(130, 48, 5);
    }
} 