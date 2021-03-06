import static com.github.forax.pro.Pro.*;
import static com.github.forax.pro.builder.Builders.*;

pro.loglevel("verbose")

resolver.
      checkForUpdate(true).
      dependencies(
          // JUnit 5
          "org.junit.jupiter.api:5.6.2",
          "org.junit.jupiter.params:5.6.2",
          "org.junit.platform.commons:1.6.2",
          "org.apiguardian.api:1.1.0",
          "org.opentest4j:1.2.0",

          // JMH
          "org.openjdk.jmh=org.openjdk.jmh:jmh-core:1.23",
          "org.openjdk.jmh.generator=org.openjdk.jmh:jmh-generator-annprocess:1.23",
          "org.apache.commons.math3=org.apache.commons:commons-math3:3.3.2",
          "net.sf.jopt-simple=net.sf.jopt-simple:jopt-simple:4.6"
      )

compiler.
      enablePreview(true).
      sourceRelease(15).
      processorModuleTestPath(path("deps")). // enable JMH annotation processor
      rawArguments("--default-module-for-created-files", "fr.umlv.vector")

packager.
      modules(
          "fr.umlv.jruntime@1.0/fr.umlv.jruntime.Main",
          "fr.umlv.vector@1.0/fr.umlv.vector.Main");

runner.
    enablePreview(true).
    module("fr.umlv.vector")

run(resolver, modulefixer, compiler, tester, packager, runner)

pro.arguments().forEach(plugin -> run(plugin))   // run command line defined plugins

/exit errorCode()