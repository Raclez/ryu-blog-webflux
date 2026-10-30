package com.ryu.blog.config;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 找出编译产物里的项目类，供字节码护栏测试扫描。
 */
final class ProjectClasses {

    private ProjectClasses() {
    }

    /**
     * @return {@code com/ryu/blog/**} 下的全部类（加载不了的会跳过）
     */
    static List<Class<?>> load() throws IOException {
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
                classes.add(Class.forName(className, false, ProjectClasses.class.getClassLoader()));
            } catch (Throwable ignored) {
                // 加载不了的类跳过（可选依赖缺失等）
            }
        }
        return classes;
    }

    /**
     * 读取某个类的字节码。
     */
    static byte[] bytecodeOf(Class<?> clazz) throws IOException {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        ClassLoader loader = clazz.getClassLoader();
        try (var in = loader.getResourceAsStream(resourceName)) {
            return in == null ? null : in.readAllBytes();
        }
    }
}
