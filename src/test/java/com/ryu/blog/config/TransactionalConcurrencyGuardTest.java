package com.ryu.blog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「{@code @Transactional} 里并发跑多条数据库查询」的护栏。
 *
 * <p><b>为什么这是硬约束</b>：R2DBC 事务会把整条响应式链固定在<b>同一条</b>连接上。
 * r2dbc-mysql 1.4.0 的每条连接持有一个 {@code RequestQueue}，底层是
 * {@code Queues.small()} 的 {@code SpscArrayQueue} —— <b>单生产者</b>队列，
 * 而 {@code submit()} 调 {@code offer()} 时<b>不持有内部锁</b>
 * （那把 {@code ReentrantLock} 只在 {@code cancelAll} 里用）。两个线程并发提交时，
 * 后到者会读到尚未推进的 {@code producerIndex} 而误判队满，抛出
 * {@code IllegalStateException: Request queue is full}，
 * 请求在真正发上线之前就被客户端自己取消。
 *
 * <p>2026-10-28 的线上事故就是这个形态：{@code ArticleQueryService#getArticlePageVO}
 * 里的 {@code Mono.zip} 撞上队列竞态，{@code onErrorResume} 又只打了
 * {@code e.getMessage()}，于是页面照常打开、作者名却是空的。
 *
 * <p><b>怎么区分串行与并发</b>：{@code Mono.flatMap}、{@code Flux.concatMap}、
 * {@code Flux.flatMapSequential} 都是<b>串行</b>的 —— 前一个元素处理完才处理下一个；
 * 只有 {@code zip} / {@code merge} / {@code Flux.flatMap} 这类会同时订阅多个 Publisher。
 *
 * <p><b>怎么区分有事务与无事务</b>：本护栏只看带 {@code @Transactional} 的方法。
 * 纯读方法没有事务，每条查询各自从池里取连接，{@code zip} 是安全的 ——
 * 前台的 {@code ArticleViewAssembler#batchGetPostRelatedData} 就是这种写法。
 *
 * <p><b>修法有两条</b>：把并发算子换成串行算子（需要事务时），
 * 或者去掉事务让每条查询各自取池连接（纯读场景）。
 */
class TransactionalConcurrencyGuardTest {

    @Test
    @DisplayName("@Transactional 方法内不得并发执行多条数据库查询")
    void transactionalMethodsMustNotRunQueriesConcurrently() throws Exception {
        Map<String, String> violations = new LinkedHashMap<>();

        for (Class<?> clazz : ProjectClasses.load()) {
            for (String hit : violationsIn(clazz)) {
                violations.put(clazz.getSimpleName() + "#" + hit, clazz.getName());
            }
        }

        assertThat(violations.keySet())
                .as("""
                        这些 @Transactional 方法里用了并发算子（zip / merge / Flux.flatMap）。

                        事务会把整条链绑到同一条 R2DBC 连接上，而 r2dbc-mysql 1.4.0 的
                        RequestQueue 是 SPSC 队列且 submit() 无锁 —— 并发提交会误判队满，
                        抛 `IllegalStateException: Request queue is full`，
                        并被 onErrorResume 静默降级成空数据。

                        修法二选一：
                          1) 需要事务 → 把并发算子换成串行算子
                             （zip → 顺序 flatMap 链，Flux.flatMap → concatMap）；
                          2) 纯读方法 → 去掉 @Transactional，让每条查询各自取池连接。
                        """)
                .isEmpty();
    }

    /**
     * 找出某个类里所有「事务方法用了并发算子」的地方。
     *
     * @return 形如 {@code 方法名 → Flux.flatMap} 的描述
     */
    static List<String> violationsIn(Class<?> clazz) throws Exception {
        List<String> violations = new ArrayList<>();

        boolean classLevelTransaction =
                AnnotatedElementUtils.findMergedAnnotation(clazz, Transactional.class) != null;

        byte[] bytecode = ProjectClasses.bytecodeOf(clazz);
        if (bytecode == null) {
            return violations;
        }

        for (Method method : clazz.getDeclaredMethods()) {
            boolean transactional = classLevelTransaction
                    || AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class) != null;
            if (!transactional) {
                continue;
            }
            for (String operator : TransactionalConcurrencyScanner.scanMethod(bytecode, clazz, method)) {
                violations.add(method.getName() + " → " + operator);
            }
        }
        return violations;
    }
}
