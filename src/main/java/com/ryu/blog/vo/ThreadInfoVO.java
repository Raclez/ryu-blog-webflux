package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 线程信息VO
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "线程信息")
public class ThreadInfoVO {

    @Schema(description = "线程ID")
    private Long threadId;

    @Schema(description = "线程名称")
    private String threadName;

    @Schema(description = "线程状态")
    private String threadState;

    @Schema(description = "是否为守护线程")
    private Boolean daemon;

    @Schema(description = "线程优先级")
    private Integer priority;

    @Schema(description = "线程组名称")
    private String threadGroupName;

    @Schema(description = "线程栈信息")
    private List<String> stackTrace;

    @Schema(description = "锁定的监视器")
    private List<String> lockedMonitors;

    @Schema(description = "锁定的同步器")
    private List<String> lockedSynchronizers;

    @Schema(description = "CPU时间(纳秒)")
    private Long cpuTime;

    @Schema(description = "用户时间(纳秒)")
    private Long userTime;
}
