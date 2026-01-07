package com.ryu.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GC信息VO
 * 
 * @author ryu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "GC信息")
public class GCInfoVO {

    @Schema(description = "GC收集器列表")
    private List<GCCollector> collectors;

    @Schema(description = "总GC次数")
    private Long totalGcCount;

    @Schema(description = "总GC时间(ms)")
    private Long totalGcTime;

    /**
     * GC收集器信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "GC收集器信息")
    public static class GCCollector {
        @Schema(description = "GC名称")
        private String name;

        @Schema(description = "GC次数")
        private Long collectionCount;

        @Schema(description = "GC总时间(ms)")
        private Long collectionTime;

        @Schema(description = "平均GC时间(ms)")
        private Double avgCollectionTime;
    }
}
