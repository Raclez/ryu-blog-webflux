package com.ryu.blog.enums;

import lombok.Getter;

/**
 * 内容语气枚举
 * 
 * <p>定义了AI生成内容时可选择的语气风格。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Getter
public enum ContentTone {

    /**
     * 正式 - 适用于学术、商务等正式场合
     */
    FORMAL("formal", "正式"),

    /**
     * 随意 - 轻松、非正式的表达方式
     */
    CASUAL("casual", "随意"),

    /**
     * 专业 - 专业领域的技术性表达
     */
    PROFESSIONAL("professional", "专业"),

    /**
     * 友好 - 亲切、易于理解的表达方式
     */
    FRIENDLY("friendly", "友好");

    private final String code;
    private final String description;

    ContentTone(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ContentTone fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ContentTone tone : values()) {
            if (tone.code.equals(code)) {
                return tone;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
