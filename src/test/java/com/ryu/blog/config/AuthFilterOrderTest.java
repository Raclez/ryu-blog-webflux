package com.ryu.blog.config;

import cn.dev33.satoken.reactor.filter.SaTokenContextFilterForReactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴权相关过滤器的顺序必须是<b>显式</b>钉死的。
 *
 * <p><b>它守的是什么</b>：早先 {@code UserPermissionWarmupFilter} 挂的是
 * {@code Ordered.HIGHEST_PRECEDENCE}，也就是排在框架的
 * {@code SaTokenContextFilterForReactor}（-104）<b>之前</b>。后果是它必须自己
 * {@code SaReactorSyncHolder.setContext(exchange)} 才能读到 token，
 * 而那个上下文建完就不管了 —— 在池化线程上留一个 box，
 * 于是「当前线程上有没有 box」失去了意义（这正是切面用错身份的根因）。
 *
 * <p>现在顺序是显式的：
 * <pre>
 *   SaTokenContextFilterForReactor (-104)  建立上下文
 *   SaTokenCorsFilterForReactor    (-103)  CORS
 *   SaFirewallCheckFilterForReactor(-102)  防火墙
 *   UserPermissionWarmupFilter     (-101)  权限快照预热
 *   SaTokenAuthFilter              (-100)  登录校验 + 采集身份
 * </pre>
 *
 * <p>预热必须在鉴权<b>之前</b>：鉴权过滤器采集操作者身份时会读权限快照
 * （{@code canManageOthers}），快照没就绪那个标记会恒为 false。
 *
 * @author ryu
 */
class AuthFilterOrderTest {

    @Test
    @DisplayName("预热过滤器必须排在框架上下文过滤器之后、鉴权过滤器之前")
    void warmupSitsBetweenFrameworkContextAndAuth() {
        int frameworkContext = orderOf(SaTokenContextFilterForReactor.class);
        int warmup = orderOf(UserPermissionWarmupFilter.class);
        int auth = orderOf(SaTokenAuthFilter.class);

        assertTrue(warmup > frameworkContext,
                "预热过滤器必须排在框架的上下文过滤器(" + frameworkContext + ")之后 —— "
                        + "排在它前面就得自己建 Sa-Token 上下文，而那个上下文会在池化线程上留残留");
        assertTrue(warmup < auth,
                "预热过滤器必须排在鉴权过滤器(" + auth + ")之前 —— "
                        + "鉴权过滤器采集身份时要读权限快照");
    }

    @Test
    @DisplayName("鉴权过滤器的顺序必须是显式声明的，不能靠继承框架父类")
    void authFilterOrderIsDeclaredExplicitly() {
        Order authOrder = AnnotatedElementUtils.findMergedAnnotation(SaTokenAuthFilter.class, Order.class);
        assertNotNull(authOrder,
                "SaTokenAuthFilter 上必须显式写 @Order —— 它现在是自己的 WebFilter，"
                        + "不再继承框架的 SaReactorFilter（那个的 @Order 是框架的实现细节，改了就静默失效）");
        // @Order 值越小越靠前：预热 -101 在前，鉴权 -100 在后
        assertTrue(authOrder.value() > orderOf(UserPermissionWarmupFilter.class),
                "鉴权过滤器(" + authOrder.value() + ")必须排在预热过滤器之后");
    }

    @Test
    @DisplayName("能力校验过滤器排在鉴权过滤器之后")
    void capabilityFilterRunsAfterAuthFilter() {
        assertTrue(orderOf(PermissionCheckFilter.class) > orderOf(SaTokenAuthFilter.class),
                "能力校验必须在登录校验之后 —— 否则还没确认登录就去判权限了");
    }

    /**
     * 取一个类的 {@code @Order} 值；没写就是 {@link Ordered#LOWEST_PRECEDENCE}。
     *
     * <p>用 {@link AnnotatedElementUtils} 而不是 {@code getAnnotation}：
     * 后者不查父类，而 {@code SaTokenAuthFilter} 的顺序正是从
     * {@code SaReactorFilter} 继承来的。
     */
    private int orderOf(Class<?> type) {
        Order order = AnnotatedElementUtils.findMergedAnnotation(type, Order.class);
        return order == null ? Ordered.LOWEST_PRECEDENCE : order.value();
    }
}
