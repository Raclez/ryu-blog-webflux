package com.ryu.blog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份相关的缓存：<b>key 与判定必须同源</b>。
 *
 * <p><b>它守的是什么</b>：本项目里有两处能拿到「当前是谁」——
 * <ul>
 *   <li>{@code SaTokenUtils.currentUserIdOrNull()} / {@code currentCanManageOthers()}
 *       —— 走 ThreadLocal，<b>缓存 key 的 SpEL 只能同步取值，只能用它</b>；</li>
 *   <li>{@code OperatorContexts.from(ctx)} —— 走 Reactor Context，归属校验用它。</li>
 * </ul>
 *
 * <p>这两条路给出的答案必须一致。不一致的后果不是「算错」，而是
 * <b>私有内容被写进别人的 key 下面</b>：
 * key 按 B 落库、判定按 A 放行 → A 看到的私有详情被缓存到了 B 的槽位上，
 * 之后任何命中那个 key 的请求都能读到。
 *
 * <p>切面保证了两条路在 HTTP 请求里一致
 * （见 {@code IdentitySourceConsistencyTest}），但那是<b>运行时</b>的一致性。
 * 这里守的是<b>代码层面</b>的一致性：凡是 key 里用了 {@code SaTokenUtils} 的方法，
 * 它的方法体里就不许再出现 {@code OperatorContexts.from} —— 两个来源混用，
 * 迟早会在某个不经过切面的路径上分叉。
 *
 * <p>判定用<b>字节码</b>而不是源码文本：javac 会把 lambda 体编译成独立的合成方法，
 * 只看方法体会漏掉「判定写在 lambda 里」这一整类写法
 * （与 {@code TransactionalConcurrencyScanner} 同一个理由）。
 *
 * @author ryu
 */
class CacheIdentitySourceGuardTest {

    @Test
    @DisplayName("缓存 key 用 SaTokenUtils 身份的方法，方法体里不得再用 OperatorContexts 取身份")
    void identityDependentCacheMustNotMixIdentitySources() throws Exception {
        List<String> violations = new ArrayList<>();

        for (Class<?> clazz : ProjectClasses.load()) {
            for (Method method : clazz.getMethods()) {
                Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);
                if (cacheable == null || cacheable.key().isEmpty()) {
                    continue;
                }
                // 只管「key 依赖身份」的方法
                if (!cacheable.key().contains("SaTokenUtils")) {
                    continue;
                }
                if (callsOperatorContextsFrom(clazz, method)) {
                    violations.add(clazz.getSimpleName() + "#" + method.getName()
                            + " —— key 用 SaTokenUtils（ThreadLocal），方法体却用 OperatorContexts（Reactor Context）");
                }
            }
        }

        assertThat(violations)
                .as("身份相关的缓存方法里混用了两个身份来源 —— 判定与 key 可能落到不同的人身上")
                .isEmpty();
    }

    /**
     * 目标方法（含它自己的 lambda 合成方法）里是否调用了 {@code OperatorContexts.from}。
     */
    private boolean callsOperatorContextsFrom(Class<?> clazz, Method javaMethod) throws Exception {
        byte[] bytecode = ProjectClasses.bytecodeOf(clazz);
        if (bytecode == null) {
            return false;
        }
        String descriptor = Type.getMethodDescriptor(javaMethod);
        String lambdaPrefix = "lambda$" + javaMethod.getName() + "$";

        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        for (MethodNode method : classNode.methods) {
            boolean isTarget = method.name.equals(javaMethod.getName()) && method.desc.equals(descriptor);
            boolean isOwnLambda = method.name.startsWith(lambdaPrefix);
            if (!isTarget && !isOwnLambda) {
                continue;
            }
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call
                        && call.owner.endsWith("OperatorContexts")
                        && "from".equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
