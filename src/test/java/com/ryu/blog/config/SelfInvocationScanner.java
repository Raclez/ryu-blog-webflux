package com.ryu.blog.config;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用字节码找出「类内部直接调用自身 {@code @Cacheable} 方法」的地方。
 *
 * <p>判定要同时看<b>owner</b>和<b>接收者</b>：
 *
 * <ul>
 *   <li>owner 是当前类 → 调用的就是本类的方法；</li>
 *   <li>接收者是 {@code this} → <b>自调用</b>，在 CGLIB 代理里绕过了拦截器。</li>
 * </ul>
 *
 * <p>接收者必须靠<b>栈数据流</b>才能确定，不能靠「上一条指令是不是 ALOAD 0」：
 * {@code this.foo(bar())} 里 {@code this} 是在两条指令之前压栈的，
 * 中间还夹着参数的求值。
 *
 * <p>所以这里用 ASM 的 {@code Analyzer} + {@code SourceInterpreter} 做完整的帧分析，
 * 取出接收者所在栈槽的<b>来源指令</b> —— 只有来源是 {@code ALOAD 0}（即 {@code this}）
 * 才算自调用。走 {@code self} 代理时来源是 {@code GETFIELD}，会被正确排除。
 */
final class SelfInvocationScanner {

    private SelfInvocationScanner() {
    }

    /**
     * @param bytecode  目标类的字节码
     * @param clazz     目标类
     * @param cached    该类里带 {@code @Cacheable} 的方法
     * @return 形如 {@code 调用方方法名 → 被调用方法名} 的描述
     */
    static List<String> scan(byte[] bytecode, Class<?> clazz, List<Method> cached) {
        Set<String> cachedNames = new LinkedHashSet<>();
        for (Method method : cached) {
            cachedNames.add(method.getName());
        }
        if (cachedNames.isEmpty()) {
            return List.of();
        }

        String internalName = clazz.getName().replace('.', '/');
        List<String> hits = new ArrayList<>();

        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        for (MethodNode method : classNode.methods) {
            if (method.instructions == null || method.instructions.size() == 0) {
                continue;
            }
            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
            Frame<SourceValue>[] frames;
            try {
                frames = analyzer.analyze(classNode.name, method);
            } catch (AnalyzerException e) {
                // 分析不了的方法跳过 —— 不能因为工具的局限就误报
                continue;
            }

            int index = 0;
            for (AbstractInsnNode insn : method.instructions) {
                Frame<SourceValue> frame = index < frames.length ? frames[index] : null;
                index++;
                if (frame == null || !(insn instanceof MethodInsnNode call)) {
                    continue;
                }
                if (call.getOpcode() == Opcodes.INVOKESTATIC || call.getOpcode() == Opcodes.INVOKESPECIAL) {
                    // 静态方法没有接收者；INVOKESPECIAL 是构造器 / super 调用
                    continue;
                }
                if (!internalName.equals(call.owner) || !cachedNames.contains(call.name)) {
                    continue;
                }
                if (receiverIsThis(frame, call)) {
                    hits.add(method.name + " → " + call.name);
                }
            }
        }

        return hits;
    }

    /**
     * 判断这次调用的接收者是不是 {@code this}。
     */
    private static boolean receiverIsThis(Frame<SourceValue> frame, MethodInsnNode call) {
        // 调用前的栈布局：[..., 接收者, 参数1, 参数2, ...]
        int argSlots = 0;
        for (Type argType : Type.getArgumentTypes(call.desc)) {
            argSlots += argType.getSize();
        }
        int receiverIndex = frame.getStackSize() - argSlots - 1;
        if (receiverIndex < 0) {
            return false;
        }

        SourceValue receiver = frame.getStack(receiverIndex);
        if (receiver == null) {
            return false;
        }
        for (AbstractInsnNode source : receiver.insns) {
            if (source instanceof VarInsnNode var && var.getOpcode() == Opcodes.ALOAD && var.var == 0) {
                return true;
            }
        }
        return false;
    }
}
