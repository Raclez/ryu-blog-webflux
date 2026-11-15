package com.ryu.blog.utils;

import com.ryu.blog.dto.OperatorContext;
import reactor.util.context.ContextView;

/**
 * 操作者身份在 <b>Reactor Context</b> 里的存取。
 *
 * <p><b>为什么走 Reactor Context 而不是 ThreadLocal</b>：
 * Reactor Context 跟着数据流走，<b>跨线程仍然读得到</b>；
 * ThreadLocal 只在「当前线程」有效，响应式链上一换线程就丢 ——
 * 这正是 {@code OperatorContext} 当初被作为参数层层传递的原因。
 *
 * <p>写入点见 {@code SaTokenAuthFilter}（请求线程上采集一次），
 * 读取点见 {@link #from(ContextView)}。
 *
 * @author ryu
 * @since 1.0.0
 */
public final class OperatorContexts {

    /**
     * 操作者身份在 Reactor Context 里的 key。
     */
    public static final String KEY = "ryu.operator";

    private OperatorContexts() {
    }

    /**
     * 在<b>请求线程</b>上采集当前操作者身份。
     *
     * <p>必须在 Sa-Token 的 ThreadLocal 上下文还在时调用
     * （即 Sa-Token 的过滤器刚跑完、下游还没开始时）。
     *
     * @return 当前操作者；取不到登录态时返回 {@link OperatorContext#ANONYMOUS}
     */
    public static OperatorContext capture() {
        return OperatorContext.current();
    }

    /**
     * 从 Reactor Context 里取操作者身份。
     *
     * <p>取不到就是匿名 —— 权限校验会因此 fail-closed（拒绝），不会误放行。
     *
     * @param contextView Reactor Context 视图
     * @return 当前操作者；没有就是 {@link OperatorContext#ANONYMOUS}
     */
    public static OperatorContext from(ContextView contextView) {
        if (contextView == null) {
            return OperatorContext.ANONYMOUS;
        }
        OperatorContext operator = contextView.getOrDefault(KEY, OperatorContext.ANONYMOUS);
        return operator == null ? OperatorContext.ANONYMOUS : operator;
    }
}
