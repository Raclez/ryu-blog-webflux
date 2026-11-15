package com.ryu.blog.config;

import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.stp.StpLogic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Sa-Token 的基础配置。
 *
 * <p><b>鉴权分层</b>（整改后的分工）：
 * <ol>
 *   <li><b>登录校验</b> —— {@link SaTokenAuthFilter}（{@code @Order(-100)}）：
 *       白名单按真实路由列，鉴权失败<b>交给全局异常处理</b>
 *       （{@code GlobalExceptionHandler}）渲染；</li>
 *   <li><b>能力校验</b> —— {@link PermissionCheckFilter}（{@code @Order(-99)}）：
 *       在建 handler 之前判权限，所以 403 先于 400 / 参数解析；
 *       Controller 方法上的 {@code @SaCheckPermission} 由
 *       {@link SaPermissionAspect} 兜底；</li>
 *   <li><b>归属校验</b> —— Service 层的 {@code OwnershipPolicy}（本人 or 管理他人）。</li>
 * </ol>
 *
 * <p><b>为什么这里没有 {@code SaReactorFilter} 了</b>：
 * 官方的 {@code SaReactorFilter} 把鉴权失败交给 {@code setError} 回调，
 * 回调必须<b>返回一段字符串</b>、由框架当纯文本写回（还不给状态码）——
 * 于是每个用它的项目都得自己再写一遍渲染逻辑，
 * 结果是<b>同一个 401/403 有两套 body</b>。
 *
 * <p>现在两条鉴权路径都把异常交给 {@code HandlerExceptionResolver}
 * （里面就是 {@code GlobalExceptionHandler}），body 只有一种。
 *
 * @author ryu
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SaTokenConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 使用JWT无状态登录
     */
    @Bean
    public StpLogic stpLogic() {
        return new StpLogicJwtForMixin();
    }
}
