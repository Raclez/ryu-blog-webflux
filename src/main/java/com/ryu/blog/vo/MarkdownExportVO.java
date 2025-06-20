package com.ryu.blog.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Markdown导出结果视图对象
 * 包含导出的Markdown内容和文件名
 *
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkdownExportVO {
    
    /**
     * 导出的Markdown内容
     */
    private String content;
    
    /**
     * 导出的文件名
     */
    private String filename;
} 