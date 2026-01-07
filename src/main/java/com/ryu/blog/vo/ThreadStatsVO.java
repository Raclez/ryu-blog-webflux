package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 线程统计信息VO
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "线程统计信息")
public class ThreadStatsVO {

    @Schema(description = "总线程数")
    private Integer totalThreadCount;

    @Schema(description = "守护线程数")
    private Integer daemonThreadCount;

    @Schema(description = "峰值线程数")
    private Integer peakThreadCount;

    @Schema(description = "活动线程数")
    private Integer activeThreadCount;

    @Schema(description = "各状态线程数统计")
    private Map<String, Integer> threadStateCount;

    @Schema(description = "线程CPU使用率前N名")
    private List<ThreadInfoVO> topThreadsByCpu;

    @Schema(description = "启动以来创建的线程总数")
    private Long totalStartedThreadCount;

    @Schema(description = "死锁线程ID")
    private List<Long> deadlockedThreadIds;
}
