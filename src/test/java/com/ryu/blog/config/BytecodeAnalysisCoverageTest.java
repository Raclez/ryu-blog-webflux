package com.ryu.blog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字节码分析覆盖率护栏。
 *
 * <p>{@link CacheSelfInvocationGuardTest} 靠 ASM 的 {@code Analyzer} 做栈数据流分析，
 * 找出「类内部直接调用自身 {@code @Cacheable} 方法」的地方。分析不出来的方法会被
 * <b>静默跳过</b> —— 那样护栏就等于漏检，隐患反而更危险（看上去有护栏，实际不生效）。
 *
 * <p>所以这里断言：项目里<b>没有任何一个方法</b>因为 {@code AnalyzerException} 而分析失败。
 * 一旦将来升级 ASM 或改动字节码导致分析器失效，这个测试会先失败，提醒我们处理。
 */
class BytecodeAnalysisCoverageTest {

    @Test
    @DisplayName("所有方法都必须能被 ASM 分析 —— 否则自调用护栏会漏检")
    void everyMethodMustBeAnalyzable() throws IOException {
        List<String> unanalyzable = new ArrayList<>();
        int analyzed = 0;

        for (Path root : classRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith(".class"))::iterator) {
                    byte[] bc = Files.readAllBytes(p);
                    ClassNode node = new ClassNode();
                    new ClassReader(bc).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    for (MethodNode m : node.methods) {
                        if (m.instructions == null || m.instructions.size() == 0) {
                            continue;
                        }
                        analyzed++;
                        try {
                            new Analyzer<>(new SourceInterpreter()).analyze(node.name, m);
                        } catch (AnalyzerException e) {
                            unanalyzable.add(node.name + "#" + m.name + " -> " + e.getMessage());
                        }
                    }
                }
            }
        }

        assertThat(unanalyzable)
                .as("""
                        这些方法 ASM 分析不了，自调用护栏会静默跳过它们 ——
                        护栏看上去存在，实际在这些方法上是瞎的。

                        通常是 ASM 版本与字节码版本不匹配，或方法里有非常规字节码。
                        """)
                .isEmpty();
        assertThat(analyzed).as("至少要扫到一些方法，否则护栏根本没在工作").isGreaterThan(100);
    }

    private static List<Path> classRoots() {
        return List.of(Path.of("target/classes"), Path.of("target/test-classes"));
    }
}
