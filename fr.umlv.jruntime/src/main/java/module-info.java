module fr.umlv.jruntime {
  requires static jdk.incubator.vector;
  requires java.compiler;

  exports fr.umlv.jruntime;

  uses javax.tools.Tool;
}