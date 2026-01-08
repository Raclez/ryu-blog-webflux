package com.ryu.blog.controller;

import com.ryu.blog.dto.AiUsageStatistics;
import com.ryu.blog.entity.AiUsageQuota;
import com.ryu.blog.service.AiUsageStatisticsService;
import com.ryu.blog.service.RateLimitService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * AI配额控制器
 * 
 * <p>提供AI使用配额和统计查询接口。
 * 
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Validated
@Tag(name = "AI配额", description = "AI使用配额和统计接口")
public class AiQuotaController {

    private final RateLimitService rateLimitService;
    private final AiUsageStatisticsService statisticsService;

    @GetMapping("/quota")
    @Operation(summary = "获取用户配额", description = "获取当前用户的AI使用配额信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(schema = @Schema(implementation = AiUsageQuota.class))),
            @ApiResponse(responseCode = "404", description = "配额不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<AiUsageQuota>> getQuota(
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("获取用户配额: userId={}", userId);
        return rateLimitService.getQuota(userId)
                .map(Result::success);
    }

    @GetMapping("/quota/remaining")
    @Operation(summary = "获取剩余配额", description = "获取用户的剩余配额信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<RateLimitService.QuotaRemaining>> getRemainingQuota(
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("获取剩余配额: userId={}", userId);
        return rateLimitService.getRemainingQuota(userId)
                .map(Result::success);
    }

    @GetMapping("/usage")
    @Operation(summary = "获取使用统计", description = "获取用户的AI使用统计信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(schema = @Schema(implementation = AiUsageStatistics.class))),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<AiUsageStatistics>> getUsageStatistics(
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId,
            @Parameter(description = "开始日期（格式：yyyy-MM-dd）") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（格式：yyyy-MM-dd）") @RequestParam(required = false) String endDate) {
        log.info("获取使用统计: userId={}, startDate={}, endDate={}", userId, startDate, endDate);
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        return statisticsService.getStatistics(userId, start.atStartOfDay(), end.atTime(23, 59, 59))
                .map(Result::success);
    }

    @GetMapping("/usage/today")
    @Operation(summary = "获取今日使用统计", description = "获取用户今日的AI使用统计")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(schema = @Schema(implementation = AiUsageStatistics.class))),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<AiUsageStatistics> getTodayUsage(
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("获取今日使用统计: userId={}", userId);
        return statisticsService.getDailyStatistics(userId);
    }

    @GetMapping("/usage/month")
    @Operation(summary = "获取本月使用统计", description = "获取用户本月的AI使用统计")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(schema = @Schema(implementation = AiUsageStatistics.class))),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<AiUsageStatistics>> getMonthUsage(
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId) {
        log.info("获取本月使用统计: userId={}", userId);
        return statisticsService.getMonthlyStatistics(userId)
                .map(Result::success);
    }

    @GetMapping("/usage/cost")
    @Operation(summary = "获取总成本", description = "获取用户在指定时间段内的总成本")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<Result<Double>> getTotalCost(
            @Parameter(description = "用户ID") @RequestParam @NotNull Long userId,
            @Parameter(description = "开始日期（格式：yyyy-MM-dd）") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（格式：yyyy-MM-dd）") @RequestParam(required = false) String endDate) {
        log.info("获取总成本: userId={}, startDate={}, endDate={}", userId, startDate, endDate);
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        
        return statisticsService.getStatistics(userId, start.atStartOfDay(), end.atTime(23, 59, 59))
                .map(AiUsageStatistics::getTotalCost)
                .map(Result::success);
    }
}
