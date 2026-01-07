package com.ryu.blog.enums;

import lombok.Getter;

/**
 * 内容风格枚举
 * 
 * <p>定义了AI生成内容时可选择的文章风格。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Getter
public enum ContentStyle {

    /**
     * 教程 - 教学性质的内容，包含步骤和说明
     */
    TUTORIAL("tutorial", "教程"),

    /**
     * 评论 - 对某个主题的评价和分析
     */
    REVIEW("review", "评论"),

    /**
     * 新闻 - 客观报道事实和事件
     */
    NEWS("news", "新闻"),

    /**
     * 观点 - 表达个人看法和见解
     */
    OPINION("opinion", "观点");

    private final String code;
    private final String description;

    ContentStyle(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ContentStyle fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ContentStyle style : values()) {
            if (style.code.equals(code)) {
                return style;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
