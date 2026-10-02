package org.foodcraft.qa;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Dedicated to the local acceptance launcher; never included in the mod JAR. */
public final class InputGuard {
    private static final AtomicInteger cursorWarps = new AtomicInteger();
    private static final AtomicInteger cursorModes = new AtomicInteger();
    private static final AtomicInteger focusRequests = new AtomicInteger();
    private static final AtomicInteger sounds = new AtomicInteger();
    private static volatile boolean glfwProtected;
    private static volatile boolean audioProtected;

    public static void premain(String argument, Instrumentation instrumentation) {
        if (!Boolean.getBoolean("foodcraft.qa.sandbox")) {
            throw new IllegalStateException("QA guard requires the explicit sandbox launch flag");
        }
        System.getProperties().put("foodcraft.qa.guard.warps", cursorWarps);
        System.getProperties().put("foodcraft.qa.guard.modes", cursorModes);
        System.getProperties().put("foodcraft.qa.guard.focus", focusRequests);
        System.getProperties().put("foodcraft.qa.guard.sounds", sounds);
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> type,
                                             ProtectionDomain domain, byte[] original) {
                boolean glfw = "org/lwjgl/glfw/GLFW".equals(name);
                boolean audio = "org/lwjgl/openal/AL10".equals(name);
                if(Boolean.getBoolean("foodcraft.qa.details")&&("org/foodcraft/machine/MachineTransfers".equals(name)||"net/minecraft/server/network/ServerGamePacketListenerImpl".equals(name)))return traceTransfers(name,original);
                if (!glfw && !audio) return null;
                try {
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                    int[] replaced = {0};
                    ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
                        @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                                   String signature, String[] exceptions) {
                            String callback = null;
                            if (glfw && method.equals("glfwSetCursorPos") && descriptor.equals("(JDD)V")) callback = "warps";
                            if (glfw && method.equals("glfwFocusWindow") && descriptor.equals("(J)V")) callback = "focus";
                            if (audio && Set.of("alSourcePlay", "alSourcePlayv", "nalSourcePlayv").contains(method)
                                    && descriptor.endsWith(")V")) callback = "sounds";
                            if (callback != null) {
                                MethodVisitor body = super.visitMethod(access & ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT),
                                    method, descriptor, signature, exceptions);
                                body.visitCode();
                                count(body, callback);
                                body.visitInsn(Opcodes.RETURN);
                                body.visitMaxs(0, 0);
                                body.visitEnd();
                                replaced[0]++;
                                return null;
                            }
                            MethodVisitor body = super.visitMethod(access, method, descriptor, signature, exceptions);
                            if(glfw&&method.equals("glfwGetKey")&&descriptor.equals("(JI)I")){
                                return new MethodVisitor(Opcodes.ASM8,body){
                                    @Override public void visitCode(){
                                        super.visitCode();Label nativeRead=new Label();
                                        visitLdcInsn("foodcraft.qa.shift");visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Boolean","getBoolean","(Ljava/lang/String;)Z",false);
                                        visitJumpInsn(Opcodes.IFEQ,nativeRead);visitVarInsn(Opcodes.ILOAD,2);visitLdcInsn(340);Label shifted=new Label();visitJumpInsn(Opcodes.IF_ICMPEQ,shifted);
                                        visitVarInsn(Opcodes.ILOAD,2);visitLdcInsn(344);visitJumpInsn(Opcodes.IF_ICMPNE,nativeRead);visitLabel(shifted);visitInsn(Opcodes.ICONST_1);visitInsn(Opcodes.IRETURN);visitLabel(nativeRead);
                                    }
                                };
                            }
                            if (glfw && method.equals("glfwSetInputMode") && descriptor.equals("(JII)V")) {
                                replaced[0]++;
                                return new MethodVisitor(Opcodes.ASM8, body) {
                                    @Override public void visitCode() {
                                        super.visitCode();
                                        Label blocked = new Label();
                                        Label allowed = new Label();
                                        visitVarInsn(Opcodes.ILOAD, 2);
                                        visitLdcInsn(0x00033001);
                                        visitJumpInsn(Opcodes.IF_ICMPEQ, blocked);
                                        visitVarInsn(Opcodes.ILOAD, 2);
                                        visitLdcInsn(0x00033005);
                                        visitJumpInsn(Opcodes.IF_ICMPNE, allowed);
                                        visitLabel(blocked);
                                        count(this, "modes");
                                        visitInsn(Opcodes.RETURN);
                                        visitLabel(allowed);
                                    }
                                };
                            }
                            return body;
                        }
                    };
                    new ClassReader(original).accept(visitor, ClassReader.SKIP_FRAMES);
                    if (glfw ? replaced[0] != 3 : replaced[0] < 3) {
                        throw new IllegalStateException("Unexpected native binding coverage: " + name + " / " + replaced[0]);
                    }
                    if (glfw) glfwProtected = true; else audioProtected = true;
                    System.out.println("FOODCRAFT QA GUARD TRANSFORMED " + name + " protected_methods=" + replaced[0]);
                    return writer.toByteArray();
                } catch (Throwable failure) {
                    failure.printStackTrace();
                    // Instrumentation normally ignores transformer exceptions. Fail closed before native code runs.
                    Runtime.getRuntime().halt(86);
                    return null;
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String status = "FOODCRAFT QA GUARD SUMMARY glfw=" + glfwProtected + " audio=" + audioProtected
                + " blocked_cursor_warps=" + cursorWarps.get() + " blocked_cursor_modes=" + cursorModes.get()
                + " blocked_focus_requests=" + focusRequests.get() + " blocked_audio_plays=" + sounds.get();
            System.out.println(status);
            String destination = System.getProperty("foodcraft.evidence.dir");
            if (destination != null) {
                try {
                    Path root = Path.of(destination);
                    Files.createDirectories(root);
                    Files.writeString(root.resolve("qa-guard.txt"), status + "\n");
                } catch (Exception failure) {
                    failure.printStackTrace();
                }
            }
        }, "FoodCraft-QA-guard-report"));
        System.out.println("FOODCRAFT QA GUARD INSTALLED cursor_lock=false cursor_warp=false focus=false audio_play=false");
    }

    // Transformed LWJGL classes reference only JDK classes. Knot restricts external agent packages.
    private static byte[] traceTransfers(String name,byte[] original){
        // Keep existing frames: these diagnostics add no branches or game-class references.
        ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor body=super.visitMethod(access,method,descriptor,signature,exceptions);
                boolean transfer=name.equals("org/foodcraft/machine/MachineTransfers")&&method.equals("handle")&&descriptor.endsWith(")Z");
                boolean close=descriptor.equals("(Lnet/minecraft/network/protocol/game/ServerboundContainerClosePacket;)V");
                if(!transfer&&!close)return body;
                return new MethodVisitor(Opcodes.ASM8,body){
                    @Override public void visitCode(){
                        super.visitCode();
                        if(close){
                            super.visitFieldInsn(Opcodes.GETSTATIC,"java/lang/System","out","Ljava/io/PrintStream;");
                            super.visitLdcInsn("FOODCRAFT QA NATIVE CLOSE THREAD ");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Thread","currentThread","()Ljava/lang/Thread;",false);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Thread","getName","()Ljava/lang/String;",false);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/io/PrintStream","println","(Ljava/lang/String;)V",false);
                        }
                    }
                    @Override public void visitInsn(int opcode){
                        if(transfer&&opcode==Opcodes.IRETURN){
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/String","valueOf","(I)Ljava/lang/String;",false);
                            super.visitLdcInsn("FOODCRAFT QA NATIVE TRANSFER ACCEPTED=");super.visitInsn(Opcodes.SWAP);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false);
                            super.visitLdcInsn(" request=");super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false);
                            super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/String","valueOf","(Ljava/lang/Object;)Ljava/lang/String;",false);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false);
                            super.visitFieldInsn(Opcodes.GETSTATIC,"java/lang/System","out","Ljava/io/PrintStream;");super.visitInsn(Opcodes.SWAP);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/io/PrintStream","println","(Ljava/lang/String;)V",false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }

    private static void count(MethodVisitor body, String key) {
        body.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;", false);
        body.visitLdcInsn("foodcraft.qa.guard." + key);
        body.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        body.visitTypeInsn(Opcodes.CHECKCAST, "java/util/concurrent/atomic/AtomicInteger");
        body.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicInteger", "incrementAndGet", "()I", false);
        body.visitInsn(Opcodes.POP);
    }

}
