import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;

public class Patch implements Opcodes {
    public static void main(String[] args) throws Exception {
        try (JarFile input = new JarFile(args[0])) {
            patch(input, "com/charlesproxy/gui/menus/HelpMenu", Path.of(args[1]), true);
            patch(input, "com/xk72/proxy/ssl/eZEl", Path.of(args[1]), false);
        }
    }
    private static void patch(JarFile input, String name, Path output, boolean menu) throws Exception {
        ClassReader reader = new ClassReader(input.getInputStream(input.getJarEntry(name + ".class")));
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        int[] counts = new int[4];
        reader.accept(new ClassVisitor(ASM9, writer) {
            public MethodVisitor visitMethod(int access, String method, String desc, String sig, String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, method, desc, sig, exceptions);
                return new MethodVisitor(ASM9, target) {
                    public void visitCode() {
                        super.visitCode();
                        if (!menu && method.equals("yPDK") && desc.equals("(Ljava/security/cert/X509Certificate;Ljava/security/PrivateKey;)V")) {
                            super.visitVarInsn(ALOAD, 0); super.visitVarInsn(ALOAD, 1);
                            super.visitMethodInsn(INVOKESTATIC, "addon/Hooks", "caChanged", desc, false);
                            counts[1]++;
                        }
                    }
                    public void visitInsn(int opcode) {
                        if (menu && method.equals("<init>") && opcode == RETURN) {
                            super.visitVarInsn(ALOAD, 0);
                            super.visitMethodInsn(INVOKESTATIC, "addon/Hooks", "install", "(Ljavax/swing/JMenu;)V", false);
                            counts[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                    public void visitLdcInsn(Object value) {
                        if (!menu && "SHA256WITHRSA".equals(value)) {
                            super.visitMethodInsn(INVOKESTATIC, "addon/Hooks", "signatureAlgorithm", "()Ljava/lang/String;", false);
                            counts[2]++;
                        } else super.visitLdcInsn(value);
                    }
                    public void visitMethodInsn(int opcode, String owner, String methodName, String descriptor, boolean itf) {
                        if (!menu && owner.equals("javax/net/ssl/KeyManagerFactory") && methodName.equals("init")
                                && descriptor.equals("(Ljava/security/KeyStore;[C)V")) {
                            super.visitMethodInsn(INVOKESTATIC, "addon/Hooks", "initKeyManager",
                                "(Ljavax/net/ssl/KeyManagerFactory;Ljava/security/KeyStore;[C)V", false);
                            return;
                        }
                        if (!menu && owner.equals("java/security/KeyStore") && methodName.equals("setKeyEntry")
                                && descriptor.equals("(Ljava/lang/String;Ljava/security/Key;[C[Ljava/security/cert/Certificate;)V")) {
                            super.visitMethodInsn(INVOKESTATIC, "addon/Hooks", "completeChain",
                                "([Ljava/security/cert/Certificate;)[Ljava/security/cert/Certificate;", false);
                            counts[3]++;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, descriptor, itf);
                    }
                };
            }
        }, 0);
        if (menu ? counts[0] != 1 : counts[1] != 1 || counts[2] != 2 || counts[3] != 2)
            throw new IllegalStateException("Unsupported Charles bytecode: " + java.util.Arrays.toString(counts));
        Path file = output.resolve(name + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, writer.toByteArray());
        System.out.println("Patched overlay: " + name);
    }
}
