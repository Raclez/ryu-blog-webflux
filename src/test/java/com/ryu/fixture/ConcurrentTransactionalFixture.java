package com.ryu.fixture;

import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 「事务里并发执行查询」的正向对照样板。
 *
 * <p>护栏测试只有<b>真的会报警</b>才有意义 —— 一个永远返回空列表的扫描器
 * 同样能让 {@code TransactionalConcurrencyGuardTest} 通过。所以这里放一个
 * <b>刻意写错</b>的样板，让测试断言护栏能把它揪出来。
 *
 * <p><b>为什么放在 {@code com.ryu.fixture} 而不是 {@code com.ryu.blog}</b>：
 * 全项目护栏扫描的是 {@code classpath*:com/ryu/blog/**}，样板类在
 * {@code com.ryu.blog} 之下的话会被自己扫到、然后自己给自己报警。
 */
public class ConcurrentTransactionalFixture {

    /**
     * <b>违规</b>：{@code Flux.flatMap} 会并发订阅内层流 ——
     * 事务里这么做会撞上 r2dbc-mysql 的 RequestQueue 竞态。
     */
    @Transactional
    public Mono<Void> concurrentFlatMap(List<Long> ids) {
        return Flux.fromIterable(ids)
                .flatMap(id -> Mono.just(id))
                .then();
    }

    /**
     * <b>违规</b>：{@code Mono.zip} 并发合并多个 Publisher。
     */
    @Transactional
    public Mono<String> concurrentZip(Mono<String> left, Mono<String> right) {
        return Mono.zip(left, right).map(tuple -> tuple.getT1() + tuple.getT2());
    }

    /**
     * <b>合规</b>：{@code concatMap} 是串行的 —— 前一个处理完才处理下一个。
     */
    @Transactional
    public Mono<Void> sequentialConcatMap(List<Long> ids) {
        return Flux.fromIterable(ids)
                .concatMap(id -> Mono.just(id))
                .then();
    }

    /**
     * <b>合规</b>：{@code Mono.flatMap} 只处理一个元素，本身就是串行的。
     */
    @Transactional
    public Mono<Long> sequentialMonoFlatMap(Mono<Long> source) {
        return source.flatMap(id -> Mono.just(id));
    }

    /**
     * <b>合规</b>：没有 {@code @Transactional} —— 纯读方法不绑定事务，
     * 每条查询各自取池连接，并发 zip 是安全的。
     */
    public Mono<String> notTransactional(Mono<String> left, Mono<String> right) {
        return Mono.zip(left, right).map(tuple -> tuple.getT1() + tuple.getT2());
    }
}
