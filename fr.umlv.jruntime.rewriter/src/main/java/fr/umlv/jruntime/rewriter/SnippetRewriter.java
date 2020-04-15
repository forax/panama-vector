package fr.umlv.jruntime.rewriter;

import static java.nio.file.Files.readAllBytes;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.ASM8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public class SnippetRewriter {
  public static void main(String[] args) throws IOException {
    var path = Path.of("target/main/exploded/fr.umlv.jruntime/fr/umlv/jruntime/Cell$VectorizedBackend$FoldValueSnippet.class");

    var writer = new ClassWriter(COMPUTE_FRAMES);
    var index1 = writer.newConst("HOLE1"); // index 1
    var index2 = writer.newConst("HOLE2"); // index 2
    var index3 = writer.newConst("HOLE3"); // index 3
    System.out.println("indexes " + index1 + " " + index2 + " " + index3);

    var reader = new ClassReader(readAllBytes(path));
    var remapperVisitor = new ClassRemapper(writer, new Remapper() {
      @Override
      public String map(String internalName) {
        /*
        var index = internalName.lastIndexOf('/');
        if (index == -1 ) {
          return internalName;
        }
        var packageName = internalName.substring(0, index);
        if (packageName.equals("fr/umlv/jruntime")) {
          var shortName = internalName.substring(index + 1);
          return "jdk/incubator/vector/" + shortName;
        }
        */
        return internalName;
      }
    });
    reader.accept(new ClassVisitor(ASM8, remapperVisitor) {
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
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!name.equals("<clinit>") && !name.equals("<init>")) {
          super.visitAnnotation("Ljdk/internal/vm/annotation/ForceInline;", true); // add ForceInline annotation
        }
        return mv;
      }
    }, 0);
    var bytes = writer.toByteArray();
    var text = Base64.getEncoder().encodeToString(bytes);

    Files.write(Path.of("Snippet.class"), bytes);
    System.out.println("Bytecode of snippet class");
    System.out.println(text);
  }
}
