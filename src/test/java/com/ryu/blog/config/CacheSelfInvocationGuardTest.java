package com.ryu.blog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存「自调用绕过代理」的护栏。
 *
 * <p>Spring AOP 用 CGLIB 生成代理时，代理对象内部持有的 {@code this} 指向的是
 * <b>目标实例</b>而不是代理。目标类里直接写 {@code getConfig(key)} 时，
 * 调用发生在目标对象内部，{@code @Cacheable} 的拦截器<b>根本不会触发</b> ——
 * 缓存命中率永远是 0，而代码看上去「有缓存」。
 *
 * <p>这属于「写错了也不报错」的静默失效，所以用<b>字节码</b>来判定：
 *
 * <ul>
 *   <li>直接自调用编译成 {@code INVOKEVIRTUAL <当前类>.方法名} —— owner 就是当前类；</li>
 *   <li>走 {@code self} 代理编译成 {@code INVOKEINTERFACE <接口>.方法名} —— owner 是接口。</li>
 * </ul>
 *
 * <p>于是规则非常干脆：<b>类内部不允许出现 owner 为自身的 {@code @Cacheable} 方法调用</b>。
 */
class CacheSelfInvocationGuardTest {

    /**
     * 找出所有「类内部直接调用自身 @Cacheable 方法」的地方。
     */
    @Test
    @DisplayName("类内部不得直接调用自身的 @Cacheable 方法 —— 会绕过 Spring 代理")
    void cachedMethods_mustNotBeSelfInvoked() throws IOException {
        Map<String, String> violations = new LinkedHashMap<>();

        for (Class<?> clazz : projectClasses()) {
            List<Method> cached = cachedMethodsOf(clazz);
            if (cached.isEmpty()) {
                continue;
            }
            byte[] bytecode = readBytecode(clazz);
            if (bytecode == null) {
                continue;
            }
            for (String hit : SelfInvocationScanner.scan(bytecode, clazz, cached)) {
                violations.put(clazz.getSimpleName() + "#" + hit, clazz.getName());
            }
        }

        assertThat(violations.keySet())
                .as("""
                        这些地方在类内部直接调用了自身的 @Cacheable 方法。
                        Spring AOP 的 CGLIB 代理里，目标对象内部的 this 指向目标实例而不是代理，
                        拦截器不会触发 —— 缓存形同虚设（命中率恒为 0），而代码看上去「有缓存」。

                        修法：注入自身代理（@Autowired setSelf(@Lazy XxxService self)），
                        调用改成 self.xxx()。
                        """)
                .isEmpty();
    }

    /**
     * 列出某个类里「带 @Cacheable」的方法。
     *
     * <p>注意 {@code findMergedAnnotation} 会沿继承链查找 ——
     * 注解写在接口上、实现类只是 {@code @Override} 时同样算数。
     */
    private static List<Method> cachedMethodsOf(Class<?> clazz) {
        List<Method> result = new ArrayList<>();
        Method[] declared;
        try {
            declared = clazz.getDeclaredMethods();
        } catch (Throwable ignored) {
            return result;
        }
        for (Method method : declared) {
            if (AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class) != null) {
                result.add(method);
            }
        }
        return result;
    }

    private static byte[] readBytecode(Class<?> clazz) {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        ClassLoader loader = clazz.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourceName)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static List<Class<?>> projectClasses() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:com/ryu/blog/**/*.class");
        List<Class<?>> classes = new ArrayList<>();
        for (Resource resource : resources) {
            String path;
            try {
                path = resource.getURL().getPath();
            } catch (IOException e) {
                continue;
            }
            int idx = path.indexOf("com/ryu/blog/");
            if (idx < 0) {
                continue;
            }
            String className = path.substring(idx, path.length() - ".class".length()).replace('/', '.');
            try {
                classes.add(Class.forName(className, false, CacheSelfInvocationGuardTest.class.getClassLoader()));
            } catch (Throwable ignored) {
                // 加载不了的类跳过（可选依赖缺失等）
            }
        }
        return classes;
    }
}
