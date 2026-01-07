package com.ryu.blog.enums;

import lombok.Getter;

/**
 * 内容优化操作类型枚举
 * 
 * <p>定义了AI内容优化支持的各种操作类型。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Getter
public enum RefinementOperation {

    /**
     * 扩展内容 - 增加更多细节和信息
     */
    EXPAND("expand", "扩展内容"),

    /**
     * 摘要 - 提取核心内容，生成简短摘要
     */
    SUMMARIZE("summarize", "生成摘要"),

    /**
     * 重写 - 用不同的表达方式重新组织内容
     */
    REWRITE("rewrite", "重写内容"),

    /**
     * 翻译 - 将内容翻译成其他语言
     */
    TRANSLATE("translate", "翻译内容"),

    /**
     * SEO优化 - 改进内容以提高搜索引擎排名
     */
    IMPROVE_SEO("improve_seo", "SEO优化"),

    /**
     * 润色 - 改进语言表达，使内容更流畅
     */
    POLISH("polish", "润色内容");

    /**
     * 操作代码
     */
    private final String code;

    /**
     * 操作描述
     */
    private final String description;

    RefinementOperation(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举值
     * 
     * @param code 操作代码
     * @return 对应的枚举值，如果不存在则返回null
     */
    public static RefinementOperation fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RefinementOperation operation : values()) {
            if (operation.code.equals(code)) {
                return operation;
            }
        }
        return null;
    }

    /**
     * 检查代码是否有效
     * 
     * @param code 操作代码
     * @return 有效返回true，否则返回false
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
