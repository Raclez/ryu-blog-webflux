package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果包装类，兼容MyBatis-Plus的IPage结构
 *
 * @author ryu
 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "查询数据列表")
    private List<T> records = Collections.emptyList();

    @Schema(description = "总数")
    private long total = 0;

    @Schema(description = "每页显示条数")
    private long size = 10;

    @Schema(description = "当前页")
    private long current = 1;

    @Schema(description = "总页数")
    private long pages = 0;

    /**
     * 默认构造方法
     */
    public PageResult() {
    }

    /**
     * 构造方法
     *
     * @param records 记录列表
     * @param total   总记录数
     */
    public PageResult(List<T> records, long total) {
        this.records = records;
        this.total = total;
    }

    /**
     * 构造方法
     *
     * @param records 记录列表
     * @param total   总记录数
     * @param size    每页条数
     * @param current 当前页
     */
    public PageResult(List<T> records, long total, long size, long current) {
        this.records = records;
        this.total = total;
        this.size = size;
        this.current = current;
        this.pages = this.getPages();
    }

    /**
     * 从Spring Data Page对象构造
     *
     * @param page Spring Data Page对象
     */
    public PageResult(Page<T> page) {
        this.records = page.getContent();
        this.total = page.getTotalElements();
        this.size = page.getSize();
        this.current = page.getNumber() + 1; // Spring Data页码从0开始，这里+1
        this.pages = page.getTotalPages();
    }

    /**
     * 计算总页数
     *
     * @return 总页数
     */
    public long getPages() {
        if (this.size == 0) {
            return 0L;
        }
        long pages = this.total / this.size;
        if (this.total % this.size != 0) {
            pages++;
        }
        return pages;
    }

    /**
     * 是否有上一页
     *
     * @return 是否有上一页
     */
    @Schema(description = "是否有上一页")
    public boolean getHasPrevious() {
        return this.current > 1;
    }

    /**
     * 是否有下一页
     *
     * @return 是否有下一页
     */
    @Schema(description = "是否有下一页")
    public boolean getHasNext() {
        return this.current < this.pages;
    }
} 