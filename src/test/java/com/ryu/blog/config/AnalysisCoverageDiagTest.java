package com.ryu.blog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自调用扫描器的<b>正向对照</b>（positive control）。
 *
 * <p>只测「有没有报出违规」是不够的 —— 一个永远返回空列表的扫描器同样能通过
 * {@link CacheSelfInvocationGuardTest}。所以这里用一个<b>刻意写了自调用</b>的
 * 样板类，断言扫描器确实能把它揪出来，而且不会把「调用另一个实例」误判。
 *
 * <p>样板类里刻意<b>不加</b> {@code @Cacheable} 注解：
 * {@link CacheSelfInvocationGuardTest} 只扫描「带 {@code @Cacheable} 方法」的类，
 * 样板类因此不会进入全项目扫描范围，不会自己给自己报警。
 */
class AnalysisCoverageDiagTest {

    /**
     * 样板类：一个直接自调用，一个调用别的实例。
     */
    static class SelfInvocationFixture {

        /**
         * 模拟一个「被缓存的方法」。
         */
        public String cached() {
            return "cached";
        }

        /**
         * <b>自调用</b> —— 编译成 {@code INVOKEVIRTUAL 本类.cached}，
         * 接收者是 {@code this}，应当被检测出来。
         */
        public String directCall() {
            return cached();
        }

        /**
         * 调用<b>另一个</b>实例 —— 不是自调用，不应被检测出来。
         */
        public String callOnOtherInstance(SelfInvocationFixture other) {
            return other.cached();
        }
    }

    @Test
    @DisplayName("扫描器必须能揪出自调用，且不误伤「调用别的实例」")
    void scannerDetectsDirectSelfInvocationOnly() throws Exception {
        Method cached = SelfInvocationFixture.class.getDeclaredMethod("cached");
        byte[] bytecode = readBytecode(SelfInvocationFixture.class);

        List<String> hits = SelfInvocationScanner.scan(
                bytecode, SelfInvocationFixture.class, List.of(cached));

        assertThat(hits)
                .as("只应报出 directCall 这一个自调用；callOnOtherInstance 是调用别的实例，不算")
                .containsExactly("directCall → cached");
    }

    private static byte[] readBytecode(Class<?> clazz) throws IOException {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(in).as("找不到字节码: " + resourceName).isNotNull();
            return in.readAllBytes();
        }
    }
}
