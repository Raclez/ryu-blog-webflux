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
 * {@code SaTokenContextException: 未能获取有效的上下文} —— 一个 <b>500</b>。更麻烦的是它
 * 只在某些请求上出现：{@code boundedElastic} 是线程池，某条线程上若恰好残留着上一次请求
 * 建立的 box（{@code UserPermissionWarmupFilter} 建完是不清的），那次请求就「碰巧成功」。
 * 现场看起来就是「偶发的鉴权故障」，重启后第一次必挂、后面时好时坏。
 *
 * <p><b>不要</b>用 {@code try { StpUtil... } catch (SaTokenContextException e) { 403 }} 糊过去：
 * 那是把「上下文没带过来」误报成「你没有权限」，500 换成一个更难查的 403。
 *
 * <p>正确做法是<b>把上下文恢复出来</b>。exchange 并没有丢 —— {@code SaReactorFilter} 用
 * {@code contextWrite} 把它写进了 Reactor Context（key 是
 * {@code ServerWebExchange.class}），而 Reactor Context 是跟着数据流穿过线程切换的。于是：
 * <pre>
 *   SaReactorHolder.getContextAndSetSync()      // 从 Reactor Context 取回 exchange，
 *                                               // 在当前线程重建 ThreadLocal，收尾时 clear
 *       .flatMap(exchange -&gt; {
 *           鉴权();                                // 此刻 StpUtil 又能用了
 *           return proceed();                      // 调用 Controller
 *       });
 * </pre>
 * 本切面只在<b>检测到当前线程没有上下文</b>时才走这条兜底路径（见
 * {@code deferredWithRestoredContext}），正常路径不多花一次判断之外的任何代价。
 *
 * <p>同一条规律对任何「可能在别的线程上摸 StpUtil」的代码都成立，例如
 * {@code UserPermissionWarmupFilter} 刷新权限快照后的那次线程切换。
 * 回归护栏：{@code SaPermissionAspectContextTest}（把兜底分支关掉就会红）、
 * {@code UserPermissionWarmupFilterContextTest}。
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

        if (checkLogin == null && checkRole == null && checkPermission == null) {
            return joinPoint.proceed();
        }

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

        // Sa-Token 的上下文是 ThreadLocal 的，只存在于「处理这次请求的那个线程」上。
        // 但 Controller 的调用线程不一定是它：@RequestPart FilePart 的参数解析是异步的，
        // multipart 落盘会把调用切到 boundedElastic（或另一个 nio 线程）上 ——
        // 那时 StpUtil 抛 SaTokenContextException「未能获取有效的上下文」，接口 500。
        // 表现极像「偶发鉴权故障」：某个线程上刚好残留着上一次的上下文就成功，否则失败。
        // 上下文其实一直在 Reactor Context 里（SaReactorFilter 写进去的 ServerWebExchange），
        // 所以缺失时把「鉴权 + 调用」整体推迟到订阅后，在当前线程上先把它恢复出来。
        if (!isSaTokenContextPresent()) {
            return deferredWithRestoredContext(signature, joinPoint, method.getName(), check);
        }

        try {
            check.run();
        } catch (Throwable e) {
            log.debug("注解鉴权失败: method={}, error={}", method.getName(), e.getMessage());
            return toErrorPublisher(signature, e);
        }

        return joinPoint.proceed();
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
     * 当前线程上有没有可用的 Sa-Token 上下文。
     *
     * <p>注意 {@code SaReactorSyncHolder.getContext()} 在上下文缺失时是<b>直接抛</b>
     * （{@code SaTokenContextException}），不是返回 null —— 所以这里必须 try/catch。
     *
     * @return 有上下文返回 true
     */
    private boolean isSaTokenContextPresent() {
        try {
            SaReactorSyncHolder.getContext();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 上下文缺失时的兜底路径：从 Reactor Context 取回 {@code ServerWebExchange}，
     * 在当前线程上重建 Sa-Token 上下文，再鉴权并调用目标方法。
     *
     * <p>用 {@link SaReactorHolder#getContextAndSetSync()}：它读的是
     * {@code SaReactorFilter} 用 {@code contextWrite} 写进去的那个 exchange，
     * 同时负责在当前线程上 set / 收尾时 clear。
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
            return SaReactorHolder.getContextAndSetSync()
                    .flatMap(exchange -> Mono.defer(() -> {
                        try {
                            check.run();
                            return (Mono<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            log.debug("注解鉴权失败: method={}, error={}", methodName, e.getMessage());
                            return Mono.error(e);
                        }
                    }));
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            return SaReactorHolder.getContextAndSetSync()
                    .flatMapMany(exchange -> Flux.defer(() -> {
                        try {
                            check.run();
                            return (Flux<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            log.debug("注解鉴权失败: method={}, error={}", methodName, e.getMessage());
                            return Flux.error(e);
                        }
                    }));
        }

        // 非响应式返回值：没有 Reactor Context 可读，只能如实报错，不要伪装成「没权限」
        throw new IllegalStateException("Sa-Token 上下文缺失，且 " + methodName
                + " 不是响应式返回值，无法从 Reactor Context 中恢复");
    }

    /**
     * 把异常包装成对应返回类型的错误 Publisher
     *
     * <p>方法没被调用（鉴权在调用前就失败了），所以只能按声明的返回类型构造。
     */
    private Object toErrorPublisher(MethodSignature signature, Throwable error) {
        Class<?> returnType = signature.getReturnType();
        if (Flux.class.isAssignableFrom(returnType)) {
            return Flux.error(error);
        }
        if (Mono.class.isAssignableFrom(returnType)) {
            return Mono.error(error);
        }
        // 非响应式返回值：直接抛，让上层的异常处理接管
        if (error instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (error instanceof Error fatal) {
            throw fatal;
        }
        throw new IllegalStateException(error);
    }
}
