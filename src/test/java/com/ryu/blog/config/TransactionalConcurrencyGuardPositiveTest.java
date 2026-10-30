package com.ryu.blog.config;

import com.ryu.fixture.ConcurrentTransactionalFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TransactionalConcurrencyGuardTest} 的<b>正向对照</b>。
 *
 * <p>护栏测试如果永远「全绿」，也可能是因为扫描器坏了 —— 那比没有护栏更危险，
 * 因为它给人虚假的安全感。所以这里用一个<b>刻意写错</b>的样板类
 * （{@link ConcurrentTransactionalFixture}）断言：
 *
 * <ul>
 *   <li>并发算子（{@code Flux.flatMap} / {@code Mono.zip}）<b>必须</b>被报出来；</li>
 *   <li>串行算子（{@code concatMap} / {@code Mono.flatMap}）<b>不能</b>被误报；</li>
 *   <li>没有 {@code @Transactional} 的方法<b>不能</b>被扫进来。</li>
 * </ul>
 */
class TransactionalConcurrencyGuardPositiveTest {

    @Test
    @DisplayName("护栏必须能揪出并发算子，且不误伤串行写法与无事务方法")
    void guardMustDetectConcurrentOperatorsOnly() throws Exception {
        List<String> violations =
                TransactionalConcurrencyGuardTest.violationsIn(ConcurrentTransactionalFixture.class);

        assertThat(violations)
                .as("并发写法必须被报出来")
                .containsExactlyInAnyOrder(
                        "concurrentFlatMap → Flux.flatMap",
                        "concurrentZip → Mono.zip");

        assertThat(violations)
                .as("串行写法与无事务方法都不能被误报")
                .doesNotContain(
                        "sequentialConcatMap → Flux.concatMap",
                        "sequentialMonoFlatMap → Mono.flatMap",
                        "notTransactional → Mono.zip");
    }
}
