package com.ryu.blog.config;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.reactor.context.SaReactorHolder;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

/**
 * Sa-Token 注解鉴权切面（WebFlux 版）
 *
 * <p><b>为什么需要自己实现</b>：Sa-Token 的 {@code @SaCheckPermission} /
 * {@code @SaCheckRole} / {@code @SaCheckLogin} 依赖 Spring MVC 的
 * {@code SaInterceptor}（一个 {@code HandlerInterceptor}）。
 * 而本项目用的是 {@code sa-token-reactor-spring-boot3-starter} ——
 * <b>它的 classpath 上根本没有 {@code SaInterceptor}</b>
 * （starter 只注册 {@code SaTokenContextForSpringReactor} 与
 * {@code SaPathCheckFilterForReactor}）。
 *
 * <p>也就是说：直接往 Controller 上写 {@code @SaCheckPermission}，
 * <b>不会有任何效果</b> —— 注解静静躺在那儿，谁也不看它。
 * 这正是本项目「配置了但没接上」的老毛病，必须避免重演。
 *
 * <p>本切面用 Spring AOP 重新实现同一套语义：
 * <ul>
 *   <li>支持方法级与类级注解（用 {@link AnnotatedElementUtils} 合并查找）；</li>
 *   <li>支持 {@link SaMode#AND} / {@link SaMode#OR}；</li>
 *   <li>支持 {@link SaCheckPermission#orRole()}（权限或角色任一满足）；</li>
 *   <li>支持 {@link SaIgnore} 跳过；</li>
 *   <li>鉴权失败时返回 {@code Mono.error} / {@code Flux.error}，
 *       让异常统一走 {@code GlobalExceptionHandler}（NotPermissionException → 403）。</li>
 * </ul>
 *
 * <p><b>鉴权本身是同步的</b>：权限数据来自 {@code SaTokenStpInterface} 的
 * 进程内内存快照（由 {@code UserPermissionWarmupFilter} 在进入 Controller 前
 * 反应式预热），所以这里没有任何 IO 阻塞。
 *
 * <h3>为什么这套鉴权会「看线程脸色」</h3>
 *
 * <p>Sa-Token 的 WebFlux 支持看着是响应式的，但上下文其实是<b>纯 ThreadLocal</b>：
 * 反编译 {@code SaTokenContextForSpringReactor} 会发现它直接
 * {@code extends SaTokenContextForThreadLocal}，根本没有从 Reactor Context 取值的能力。
 * 那个 box（request / response / storage 三件套）由 {@code SaReactorFilter} 在请求线程上
 * {@code SaReactorSyncHolder.setContext(exchange)} 建立，之后每一次 {@code StpUtil.xxx}
 * 都去<b>当前线程</b>的 ThreadLocal 里取它。
 *
 * <p>这就藏了一个前提：<b>调用 StpUtil 的线程必须就是处理这次请求的那个线程</b>。
 * 在 WebFlux 里这个前提经常不成立：
 * <ul>
 *   <li>{@code @RequestPart FilePart} 的参数解析是异步的 —— multipart 落盘会切到
 *       {@code boundedElastic}，Spring 等参数 Mono 完成之后才 {@code flatMap} 去调用
 *       Controller，切面跟着 Controller 一起<b>跑到了别的线程</b>上；</li>
 *   <li>任何 {@code subscribeOn(Schedulers.boundedElastic())}（包装阻塞 SDK 的常见写法）；</li>
 *   <li>链路中途的 R2DBC / Redis 往返：续作在连接自己的 event-loop 线程上恢复。</li>
 * </ul>
 *
 * <p>上下文丢了之后，{@code StpUtil} 抛的是
 * {@code SaTokenContextException: 未能获取有效的上下文} —— 一个 <b>500</b>。
 *
 * <p><b>不要</b>用 {@code try { StpUtil... } catch (SaTokenContextException e) { 403 }} 糊过去：
 * 那是把「上下文没带过来」误报成「你没有权限」，500 换成一个更难查的 403。
 *
 * <p><b>更危险的不是 500，是「没报错但用错了人」。</b>
 *
 * <p>ThreadLocal 挂在池化线程上，而 box 里装的是<b>上一次请求</b>的 exchange
 * （含上一次请求的 Authorization 头）。所以「当前线程上有没有 box」
 * <b>不等于</b>「这个 box 是不是本次请求的」。早先的实现用前者当后者的判据
 * （{@code isSaTokenContextPresent()}），命中就走快路径，直接拿<b>上一个请求</b>的
 * token 做判定 —— 上一次是管理员，这一次普通用户就被放行；上一次匿名，
 * 这一次合法用户被判 401。
 *
 * <p>正确做法是<b>无条件</b>把上下文恢复出来。exchange 并没有丢 ——
 * 框架的 {@code SaTokenContextFilterForReactor} 用 {@code contextWrite} 把它写进了
 * Reactor Context（key 是 {@code SaReactorHolder.EXCHANGE_KEY}），
 * 而 Reactor Context 是跟着数据流穿过线程切换的，
 * 里面的 exchange <b>按构造就是本次请求的</b>。于是：
 * <pre>
 *   SaReactorHolder.getExchange(contextView)   // 从 Reactor Context 取回本次请求的 exchange
 *       .flatMap(exchange -&gt; Mono.defer(() -&gt; {
 *           SaReactorSyncHolder.setContext(exchange);   // 在当前线程重建 ThreadLocal
 *           鉴权();                                // 此刻 StpUtil 又能用了
 *           return proceed();                      // 调用 Controller
 *       }));
 * </pre>
 * 本切面<b>总是</b>走这条路径（见 {@code deferredWithRestoredContext}）——
 * 不是「检测到没有上下文才恢复」，而是「不管当前线程上有什么，都以 Reactor Context 为准」。
 * 代价只是每次多一次 {@code deferContextual}，换来的是「判定用的永远是本次请求的身份」。
 *
 * <p>同一条规律对任何「可能在别的线程上摸 StpUtil」的代码都成立，例如
 * {@code UserPermissionWarmupFilter} 刷新权限快照后的那次线程切换。
 * 回归护栏：{@code SaPermissionAspectContextTest}、
 * {@code SaPermissionAspectStaleContextTest}（残留上下文的两种方向都要挡住）。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SaPermissionAspect {

    @Around("execution(public * com.ryu.blog.controller..*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);

        if (AnnotatedElementUtils.hasAnnotation(method, SaIgnore.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, SaIgnore.class)) {
            return joinPoint.proceed();
        }

        SaCheckLogin checkLogin = findAnnotation(method, targetClass, SaCheckLogin.class);
        SaCheckRole checkRole = findAnnotation(method, targetClass, SaCheckRole.class);
        SaCheckPermission checkPermission = findAnnotation(method, targetClass, SaCheckPermission.class);

        Runnable check = () -> {
            if (checkLogin != null) {
                StpUtil.checkLogin();
            }
            if (checkRole != null) {
                checkRole(checkRole);
            }
            if (checkPermission != null) {
                checkPermission(checkPermission);
            }
        };

        // 没有鉴权注解的端点<b>也要</b>恢复上下文：
        // 控制器里有 30 多处直接 StpUtil.getLoginIdAsLong()（MenusController#getMyMenus、
        // FavoriteController、UserController#updatePassword …），而这些端点一个鉴权注解都没有 ——
        // 切面不恢复的话，它们读到的就是「当前线程上恰好残留的那个 box」，
        // 可能是<b>上一个请求</b>的身份。
        //
        // 但非响应式返回值没法用 defer 包装（没有 Reactor Context 可依赖），那类端点维持原样。
        boolean reactive = Mono.class.isAssignableFrom(signature.getReturnType())
                || Flux.class.isAssignableFrom(signature.getReturnType());
        if (checkLogin == null && checkRole == null && checkPermission == null && !reactive) {
            return joinPoint.proceed();
        }

        // 无条件从 Reactor Context 恢复上下文 —— 不要再用「当前线程上有没有 box」当判据。
        //
        // 「有没有 box」回答不了「这个 box 是不是本次请求的」：ThreadLocal 挂在池化线程上，
        // 而 box 里装的是**上一个请求**的 exchange（含上一个请求的 Authorization 头）。
        // 拿它做鉴权等于「用别人的 token 判权限」——
        // 上一次是管理员，这一次普通用户就被放行；上一次匿名，这一次合法用户被判 401。
        //
        // Reactor Context 里的 exchange 按构造就是本次请求的，没有这种歧义。
        return deferredWithRestoredContext(signature, joinPoint, method.getName(), check);
    }

    // =====================================================================
    // 具体检查
    // =====================================================================

    private void checkRole(SaCheckRole annotation) {
        String[] roles = annotation.value();
        if (roles == null || roles.length == 0) {
            return;
        }
        if (annotation.mode() == SaMode.OR) {
            StpUtil.checkRoleOr(roles);
        } else {
            StpUtil.checkRoleAnd(roles);
        }
    }

    private void checkPermission(SaCheckPermission annotation) {
        String[] permissions = annotation.value();
        String[] orRoles = annotation.orRole();

        // orRole：权限或角色任一满足即可
        if (orRoles != null && orRoles.length > 0) {
            boolean roleMatched = StpUtil.hasRoleOr(orRoles);
            boolean permissionMatched = permissions != null && permissions.length > 0
                    && StpUtil.hasPermissionOr(permissions);
            if (!roleMatched && !permissionMatched) {
                StpUtil.checkPermission(permissions == null || permissions.length == 0
                        ? "" : permissions[0]);
            }
            return;
        }

        if (permissions == null || permissions.length == 0) {
            return;
        }
        if (annotation.mode() == SaMode.OR) {
            StpUtil.checkPermissionOr(permissions);
        } else {
            StpUtil.checkPermissionAnd(permissions);
        }
    }

    // =====================================================================
    // 工具
    // =====================================================================

    private <A extends java.lang.annotation.Annotation> A findAnnotation(
            Method method, Class<?> targetClass, Class<A> annotationType) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, annotationType);
        }
        return annotation;
    }

    /**
     * 从 <b>Reactor Context</b> 里取回本次请求的 {@link ServerWebExchange}。
     *
     * <p>Reactor Context 随数据流走、跨线程仍在，而且里面的 exchange
     * <b>按构造就是本次请求的</b> —— 这是它比 ThreadLocal 强的地方：
     * ThreadLocal 只知道「这个线程上有没有 box」，不知道「box 是不是我的」。
     *
     * <p>取不到时给一个明确的异常，而不是让下游抛 {@code NullPointerException}
     * （{@code Mono.just(null)} 会直接 NPE，堆栈指向 Reactor 内部，很难定位）。
     *
     * @return 本次请求的 exchange；Reactor Context 里没有时抛 IllegalStateException
     */
    private Mono<ServerWebExchange> currentExchange() {
        return Mono.deferContextual(cv -> {
            ServerWebExchange exchange = SaReactorHolder.getExchange(cv);
            if (exchange == null) {
                return Mono.error(new IllegalStateException(
                        "Reactor Context 里没有 ServerWebExchange —— 无法恢复 Sa-Token 上下文。"
                                + "该请求很可能不是从 WebFlux 的过滤器链进来的。"));
            }
            return Mono.just(exchange);
        });
    }

    /**
     * 恢复上下文 → 鉴权 → 调用目标方法。
     *
     * <p>exchange 来自 Reactor Context（框架的 {@code SaTokenContextFilterForReactor}
     * 用 {@code contextWrite} 写进去的），<b>按构造就是本次请求的</b>。
     * 在当前线程上重建 Sa-Token 的 ThreadLocal 上下文，鉴权与目标方法都在这个作用域里执行。
     *
     * @param signature  被调用方法的签名
     * @param joinPoint  连接点
     * @param methodName 方法名（日志用）
     * @param check      鉴权动作
     * @return 与声明返回类型一致的 Publisher
     */
    private Object deferredWithRestoredContext(MethodSignature signature, ProceedingJoinPoint joinPoint,
                                               String methodName, Runnable check) {
        Class<?> returnType = signature.getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            return currentExchange()
                    .flatMap(exchange -> Mono.defer(() -> {
                        // getMonoExchange() 只「取」exchange，不建 ThreadLocal —— 必须自己建，
                        // 否则 defer 里的鉴权与 proceed() 都读不到 Sa-Token 上下文。
                        SaReactorSyncHolder.setContext(exchange);
                        try {
                            check.run();
                            return (Mono<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            log.debug("注解鉴权失败: method={}, error={}", methodName, e.getMessage());
                            return Mono.error(e);
                        }
                    }).doFinally(signal -> SaReactorSyncHolder.clearContext()));
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            return currentExchange()
                    .flatMapMany(exchange -> Flux.defer(() -> {
                        // 同上：自己建 ThreadLocal，并在收尾时清理
                        SaReactorSyncHolder.setContext(exchange);
                        try {
                            check.run();
                            return (Flux<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            log.debug("注解鉴权失败: method={}, error={}", methodName, e.getMessage());
                            return Flux.error(e);
                        }
                    }).doFinally(signal -> SaReactorSyncHolder.clearContext()));
        }

        // 非响应式返回值：没有 Reactor Context 可读，只能如实报错，不要伪装成「没权限」
        throw new IllegalStateException("Sa-Token 上下文缺失，且 " + methodName
                + " 不是响应式返回值，无法从 Reactor Context 中恢复");
    }
}
