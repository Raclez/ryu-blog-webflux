package com.ryu.blog.config;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 找出「{@code @Transactional} 方法里并发执行了多条数据库查询」的地方。
 *
 * <p>判定依据是<b>被调用方法的接收者类型</b>：
 *
 * <ul>
 *   <li>{@code Mono.zip} / {@code Mono.zipWith} —— 把多个 Publisher <b>并发</b>合并；</li>
 *   <li>{@code Flux.merge*} / {@code Flux.zip} —— 把多个 Flux <b>并发</b>合并；</li>
 *   <li>{@code Flux.flatMap} / {@code Flux.flatMapMany} —— 对每个元素<b>并发</b>订阅内层流；</li>
 *   <li>{@code Flux.parallel} —— 显式开并行流。</li>
 * </ul>
 *
 * <p>而 {@code Mono.flatMap} 是<b>串行</b>的（一个元素接着一个元素），
 * {@code Flux.concatMap} / {@code flatMapSequential} 也是串行的 —— 这些都不算违规。
 */
final class TransactionalConcurrencyScanner {

    private TransactionalConcurrencyScanner() {
    }

    /**
     * 扫描<b>单个方法</b>，返回它用到的并发算子。
     *
     * <p><b>必须连同它的 lambda 一起扫</b>：javac 会把 lambda 体编译成
     * 独立的合成方法（{@code lambda$方法名$序号}），所以
     * {@code someService.foo(x -> Mono.zip(a, b))} 里的 {@code Mono.zip}
     * <b>不在</b> {@code foo} 的字节码里，而在 {@code lambda$foo$0} 里。
     * 只扫方法体就会漏掉「并发算子写在 lambda 里」这一整类写法 ——
     * 而这恰恰是业务代码里最自然的写法。
     *
     * @param bytecode   目标类的字节码
     * @param clazz      目标类
     * @param javaMethod 要扫描的方法
     * @return 算子名（去重），没有则返回空列表
     */
    static List<String> scanMethod(byte[] bytecode, Class<?> clazz, Method javaMethod) {
        String descriptor = Type.getMethodDescriptor(javaMethod);

        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        Set<String> operators = new LinkedHashSet<>();
        String lambdaPrefix = "lambda$" + javaMethod.getName() + "$";

        for (MethodNode method : classNode.methods) {
            boolean isTarget = method.name.equals(javaMethod.getName())
                    && method.desc.equals(descriptor);
            // 该方法体里定义的 lambda：合成方法名形如 lambda$方法名$序号
            boolean isOwnLambda = method.name.startsWith(lambdaPrefix);
            if (!isTarget && !isOwnLambda) {
                continue;
            }
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (!(insn instanceof MethodInsnNode call)) {
                    continue;
                }
                String operator = concurrentOperatorOf(call);
                if (operator != null) {
                    operators.add(operator);
                }
            }
        }
        return new ArrayList<>(operators);
    }

    /**
     * 判断这次调用是不是「并发合并/并发展开」的响应式算子。
     *
     * <p><b>注意</b>：{@code Mono.zip} / {@code Flux.zip} / {@code Flux.merge}
     * 都是<b>静态</b>方法（{@code INVOKESTATIC}），而 {@code Flux.flatMap}
     * 是实例方法。静态的入口不能按「静态方法没有并发语义」一刀切掉 ——
     * {@code Mono.zip} 恰恰是最典型的并发写法。
     *
     * @return 算子名；不是并发算子则返回 {@code null}
     */
    private static String concurrentOperatorOf(MethodInsnNode call) {
        String owner = call.owner;
        String name = call.name;

        if ("reactor/core/publisher/Mono".equals(owner)) {
            switch (name) {
                case "zip":
                case "zipWith":
                case "zipDelayError":
                case "zipWhen":
                case "when":
                case "whenDelayError":
                    return "Mono." + name;
                default:
                    return null;
            }
        }

        if ("reactor/core/publisher/Flux".equals(owner)) {
            switch (name) {
                // 实例方法：对每个元素并发订阅内层流
                case "flatMap":
                case "flatMapMany":
                case "parallel":
                    return "Flux." + name;
                // 静态方法：并发合并多个 Flux
                case "merge":
                case "mergeWith":
                case "mergeComparing":
                case "mergeComparingWith":
                case "mergeOrdered":
                case "mergeOrderedWith":
                case "mergeSequential":
                case "zip":
                    return "Flux." + name;
                default:
                    return null;
            }
        }
        return null;
    }
}
