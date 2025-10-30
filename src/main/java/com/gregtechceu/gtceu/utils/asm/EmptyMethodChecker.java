package com.gregtechceu.gtceu.utils.asm;

import org.objectweb.asm.*;

import java.io.IOException;
import java.lang.reflect.Method;

public class EmptyMethodChecker extends ClassVisitor {

    public static boolean isMethodBodyEmpty(Method method) {
        return isMethodBodyEmpty(method.getDeclaringClass(),
                method.getName(),
                method.getParameterTypes());
    }

    public static boolean isMethodBodyEmpty(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            var classStream = clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.', '/') + ".class");
            if (classStream == null) return false;
            var classReader = new ClassReader(classStream);
            var analyzer = new EmptyMethodChecker(methodName, getMethodDescriptor(parameterTypes));
            classReader.accept(analyzer, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return analyzer.methodFound && analyzer.methodBodyEmpty;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String getMethodDescriptor(Class<?>[] parameterTypes) {
        StringBuilder descriptor = new StringBuilder();
        descriptor.append('(');
        for (Class<?> paramType : parameterTypes) {
            descriptor.append(Type.getDescriptor(paramType));
        }
        descriptor.append(')');
        return descriptor.toString();
    }

    private final String targetMethodName;
    private final String targetMethodDescriptor;
    private boolean methodFound = false;
    private boolean methodBodyEmpty = true;

    public EmptyMethodChecker(String methodName, String methodDescriptor) {
        super(Opcodes.ASM9);
        this.targetMethodName = methodName;
        this.targetMethodDescriptor = methodDescriptor;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        if (name.equals(targetMethodName) && descriptor.startsWith(targetMethodDescriptor)) {
            methodFound = true;
            return new MethodBodyVisitor(this);
        }
        return null;
    }

    private static class MethodBodyVisitor extends MethodVisitor {

        private final EmptyMethodChecker analyzer;
        private boolean hasNonReturnInstructions = false;

        private MethodBodyVisitor(EmptyMethodChecker analyzer) {
            super(Opcodes.ASM9);
            this.analyzer = analyzer;
        }

        @Override
        public void visitCode() {
            hasNonReturnInstructions = false;
        }

        @Override
        public void visitInsn(int opcode) {}

        @Override
        public void visitIntInsn(int opcode, int operand) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (!(opcode == Opcodes.ALOAD && var == 0)) {
                hasNonReturnInstructions = true;
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitInvokeDynamicInsn(final String name, final String descriptor, final Handle bootstrapMethodHandle, final Object... bootstrapMethodArguments) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitLdcInsn(Object value) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            hasNonReturnInstructions = true;
        }

        @Override
        public void visitEnd() {
            analyzer.methodBodyEmpty = !hasNonReturnInstructions;
        }
    }
}
