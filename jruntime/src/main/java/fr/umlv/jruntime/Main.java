package fr.umlv.jruntime;

import static java.util.function.Function.identity;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.Tool;

public class Main {
  static int jshell(InputStream in, OutputStream out, OutputStream err, String... arguments) {
    var serviceLoader = ServiceLoader.load(Tool.class, Main.class.getClassLoader());
    var jshell = serviceLoader.stream()
        .map(Provider::get)
        .filter(tool -> tool.name().equals("jshell"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("can not find jshell"));
    return jshell.run(in, out, err, arguments);
  }

  public static void main(String[] args) {
    System.out.println("|  Interactive Jruntime using JShell");
    System.out.println("|    backend " + Cell.backendVersion());
    var arguments = Stream.of(
        Stream.of("--enable-preview"),
        Stream.of("--module-path", System.getProperty("jdk.module.path")),
        Stream.of("--add-modules", System.getProperty("jdk.module.main")),
        Stream.of(Path.of("jruntime-jshell-init.jsh")).filter(Files::exists).flatMap(p -> Stream.of("--startup", p.toString()))
    ).flatMap(identity()).toArray(String[]::new);
    jshell(System.in, System.out, System.err, arguments);
  }
}
