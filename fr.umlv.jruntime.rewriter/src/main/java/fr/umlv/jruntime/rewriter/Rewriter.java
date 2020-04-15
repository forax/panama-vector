package fr.umlv.jruntime.rewriter;

import static java.nio.file.Files.readAllBytes;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

public class Rewriter {
  private static String rename(String owner) {
    if (owner.equals("fr/umlv/jruntime/Cell$VectorizedBackend$Template")) {
      return "jdk/incubator/vector/Cell$VectorizedBackend$Template";
    }
    return owner;
  }

  public static void main(String[] args) throws IOException {
    var path = Path.of("target/main/exploded/fr.umlv.jruntime/fr/umlv/jruntime/Cell$VectorizedBackend$Template.class");

    var writer = new ClassWriter(COMPUTE_FRAMES);
    var reader = new ClassReader(readAllBytes(path));
    reader.accept(new ClassVisitor(ASM8, writer) {
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, rename(name), signature, superName, interfaces);
      }

      @Override
      public void visitNestHost(String nestHost) {
        // skip !
      }

      @Override
      public void visitOuterClass(String owner, String name, String descriptor) {
        // reduce the fat
      }
      @Override
      public void visitInnerClass(String name, String outerName, String innerName, int access) {
        // reduce the fat
      }

      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, descriptor, signature, value);
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(ASM8, mv) {
          @Override
          public void visitCode() {
            if (!name.equals("<clinit>") && ((access & ACC_STATIC) != 0)) {
              super.visitAnnotation("Ljdk/internal/vm/annotation/ForceInline;", true);   // add ForceInline annotation
            }
          }

          @Override
          public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, rename(owner), name, descriptor);
          }
        };
      }
    }, 0);
    var bytes = writer.toByteArray();
    var text = Base64.getEncoder().encodeToString(bytes);

    Files.write(Path.of("Template.class"), bytes);
    System.out.println("Bytecode of template class");
    System.out.println(text);
  }
}
