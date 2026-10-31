package com.ryu.blog.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.ryu.blog.service.SysConfigService;
import com.ryu.blog.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 公开配置控制器
 *
 * <p><b>为什么单独一个类</b>：{@code SysConfigController} 上有类级
 * {@code @SaCheckPermission(SYSTEM_CONFIG_VIEW)}（配置表里有 AI Provider 的 API Key，
 * 读权限也必须卡住），而本项目的 {@code SaPermissionAspect} 会把类级注解合并到每个方法上。
 * 把公开方法塞进去，要么被一起锁死，要么得给类级注解开洞 —— 两种都不可接受。
 *
 * <p><b>{@code @SaIgnore} 不是匿名放行的全部</b>：它只让注解鉴权切面跳过，
 * {@code SaReactorFilter} 仍然要求登录。真正的匿名放行在
 * {@code SaTokenConfig.addExclude("/sysConfig/public")}，两处缺一不可。
 *
 * <p>响应只含 {@code config_key -> config_value}，不带 remark / extra / userId：
 * 那些字段没有展示价值，却有泄露风险。
 *
 * @author Ryu
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/sysConfig/public")
@RequiredArgsConstructor
@Tag(name = "公开配置", description = "站点展示类配置的匿名只读接口")
@SaIgnore
public class PublicSysConfigController {

    private final SysConfigService sysConfigService;

    /**
     * 获取公开配置
     *
     * @return 公开配置键值对
     */
    @GetMapping
    @Operation(summary = "获取公开配置", description = "只返回白名单内的键值对，可匿名访问")
    public Mono<Result<Map<String, String>>> getPublicConfig() {
        log.debug("获取公开配置");
        return sysConfigService.getPublicConfigValues()
                .map(Result::success);
    }
}
