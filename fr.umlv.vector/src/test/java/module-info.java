open module fr.umlv.vector {
  requires org.junit.jupiter.api;
  requires org.junit.jupiter.params;

  requires fr.umlv.jruntime;  // to test it

  requires org.openjdk.jmh;  // JMH support
  requires org.openjdk.jmh.generator;
}