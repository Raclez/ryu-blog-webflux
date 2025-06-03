package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 浏览历史统计信息数据传输对象
 *
 * @author ryu 475118582@qq.com
 */
@Data
@Schema(description = "浏览历史统计信息")
public class ViewHistoryStatsVO {
    
    @Schema(description = "总访问量")
    private Long totalViews;

    @Schema(description = "今日访问量")
    private Long todayViews;

    @Schema(description = "昨日访问量")
    private Long yesterdayViews;

    @Schema(description = "本周访问量")
    private Long weeklyViews;

    @Schema(description = "本月访问量")
    private Long monthlyViews;

    @Schema(description = "访问量最高的文章ID和对应的访问次数")
    private Map<Long, Integer> topPosts;

    @Schema(description = "访问来源地区分布")
    private Map<String, Integer> locationDistribution;

    @Schema(description = "设备类型分布")
    private Map<String, Integer> deviceDistribution;

    @Schema(description = "每日访问量趋势（最近7天）")
    private Map<String, Long> dailyViewsTrend;

    @Schema(description = "平均浏览时长（秒）")
    private Double averageViewDuration;

    @Schema(description = "独立访客数")
    private Long uniqueVisitors;
} 