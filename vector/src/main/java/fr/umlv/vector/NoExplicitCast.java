package fr.umlv.vector;

public class NoExplicitCast {
  public static abstract class Vector<E> {
    abstract  void foo(Vector<E> vector);
    abstract Vector<E> bar();
  }

  private static abstract class AbstractVector<E, V extends AbstractVector<E, V>> extends Vector<E> {
    public abstract void foo(V vector);
    public final void foo(Vector<E> vector) {
      foo((V)vector);
    }
    public abstract V bar();
  }

  public static abstract class IntVector extends AbstractVector<Integer, IntVector> {


  }
}
