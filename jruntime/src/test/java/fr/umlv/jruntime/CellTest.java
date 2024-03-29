package fr.umlv.jruntime;

import static fr.umlv.jruntime.Cell.Dyads.ADD;
import static fr.umlv.jruntime.Cell.Dyads.AND;
import static fr.umlv.jruntime.Cell.Dyads.COUNT;
import static fr.umlv.jruntime.Cell.Dyads.DIV;
import static fr.umlv.jruntime.Cell.Dyads.MAX;
import static fr.umlv.jruntime.Cell.Dyads.MIN;
import static fr.umlv.jruntime.Cell.Dyads.MUL;
import static fr.umlv.jruntime.Cell.Dyads.OR;
import static fr.umlv.jruntime.Cell.Dyads.SUB;
import static fr.umlv.jruntime.Cell.Monads.NEG;
import static fr.umlv.jruntime.Cell.Monads.ZOMO;
import static java.util.stream.IntStream.range;
import static java.util.stream.IntStream.rangeClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.umlv.jruntime.Cell.Dyad;
import fr.umlv.jruntime.Cell.Dyads;
import fr.umlv.jruntime.Cell.Fold;
import fr.umlv.jruntime.Cell.Monad;
import fr.umlv.jruntime.Cell.Monads;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CellTest {
  @Test
  public void of() {
    var a = Cell.of(1, 2, 3);
    var w = Cell.of(1, 2, 3);
    assertEquals(a, w);
    assertEquals(a.hashCode(), w.hashCode());
    assertEquals("[ 1,  2,  3]", a.toString());
  }

  @Test
  public void applyVectorMonad() {
    var a = Cell.of(1, 2, 3);
    var r = a.apply(NEG);
    assertEquals(Cell.of(-1, -2, -3), r);
  }
  @Test
  public void applyVectorUserDefinedMonad() {
    var monad = Monad.of(x -> 5 - x);
    var a = Cell.of(1, 2, 3);
    var r = a.apply(monad);
    assertEquals(Cell.of(4, 3, 2), r);
  }
  @Test
  public void applyVectorMonadBig() {
    var a = Cell.of(1_000).iota();
    var r = a.apply(ZOMO);
    var ints = IntStream.generate(() -> -1).limit(1_000).toArray();
    ints[0] = 0;
    assertEquals(Cell.of(ints), r);
  }


  @Test
  public void applyVectorDyad() {
    var a = Cell.of(1, 2, 3);
    var w = Cell.of(4, 5, 6);
    var r = a.apply(ADD, w);
    assertEquals(Cell.of(5, 7, 9), r);
  }
  @Test
  public void applyVectorUserDefinedDyad() {
    var dyad = Dyad.of(0, (a, b) -> a + a * b);
    var a = Cell.of(1, 2, 3);
    var w = Cell.of(4, 5, 6);
    var r = a.apply(dyad, w);
    assertEquals(Cell.of(5, 12, 21), r);
  }
  @Test
  public void applyVectorDyadBig() {
    var a = Cell.of(1_000).iota();
    var r = a.apply(ADD, a);
    var ints = IntStream.iterate(0, x -> x < 2_000, x -> x + 2).toArray();
    assertEquals(Cell.of(ints), r);
  }

  @Test
  public void testApplyVectorFold() {
    var a = Cell.of(1, 2, 3);
    var r = a.apply(ADD.fold());
    assertEquals(Cell.of(6), r);
  }
  @Test
  public void testApplyVectorFoldBig() {
    var a = Cell.of(30_000).iota();
    var r = a.apply(ADD.fold());
    assertEquals(Cell.of(449_985_000), r);
  }

  @Test
  public void testApplyMatrixFold() {
    var a = Cell.of(4, 3).iota();
    var r = a.apply(ADD.fold());
    assertEquals(Cell.of(18, 22, 26), r);
  }
  @Test
  public void testApplyMatrixFoldBig() {
    var a = Cell.of(2, 34).iota();
    var r = a.apply(ADD.fold());
    var ints = IntStream.iterate(34, i -> i <= 100, i -> i + 2).toArray();
    assertEquals(Cell.of(ints), r);
  }
  @Test
  public void testApplyMatrixFoldRank1() {
    var a = Cell.of(4, 3).iota();
    var r = a.apply(ADD.fold(1));
    assertEquals(Cell.of(3, 12, 21, 30), r);
  }
  @Test
  public void testApplyMatrixFoldRank1Big() {
    var a = Cell.of(34, 2).iota();
    var r = a.apply(ADD.fold(1));
    var ints = IntStream.iterate(1, i -> i <= 133, i -> i + 4).toArray();
    assertEquals(Cell.of(ints), r);
  }
  @Test
  public void testApplyMatrixFoldRank2() {
    var a = Cell.of(4, 3).iota();
    var r = a.apply(ADD.fold(2));
    assertEquals(Cell.of(18, 22, 26), r);
  }
  @Test
  public void testApplyMatrixFoldRank2Big() {
    var a = Cell.of(2, 34).iota();
    var r = a.apply(ADD.fold(2));
    var ints = IntStream.rangeClosed(34, 100).filter(i -> i % 2 == 0).toArray();
    assertEquals(Cell.of(ints), r);
  }

  @Test
  public void testApplyCubeFold() {
    var a = Cell.of(3, 2, 3).iota();
    var r = a.apply(ADD.fold());
    var e = Cell.of(2, 3).reshape(Cell.of(18, 21, 24, 27, 30, 33));
    assertEquals(e, r);
  }
  @Test
  public void testApplyCubeFoldRank1() {
    var a = Cell.of(2, 3, 4).iota();
    var r = a.apply(ADD.fold(1));
    var e = Cell.of(2, 3).reshape(Cell.of(6, 22, 38, 54, 70, 86));
    assertEquals(e, r);
  }
  @Test
  public void testApplyCubeFoldRank1Big() {
    var a = Cell.of(2, 3, 34).iota();
    var r = a.apply(ADD.fold(1));
    var e = Cell.of(2, 3).reshape(Cell.of(561, 1717, 2873, 4029, 5185, 6341));
    assertEquals(e, r);
  }
  @Test
  public void testApplyCubeFoldRank2() {
    var a = Cell.of(2, 3, 4).iota();
    var r = a.apply(ADD.fold(2));
    var e = Cell.of(2, 4).reshape(Cell.of(12, 15, 18, 21, 48, 51, 54, 57));
    assertEquals(e, r);
  }
  @Test
  public void testApplyCubeFoldRank2Big() {
    var a = Cell.of(2, 34, 4).iota();
    var r = a.apply(ADD.fold(2));
    var e = Cell.of(2, 4).reshape(Cell.of(2244, 2278, 2312, 2346, 6868, 6902, 6936, 6970));
    assertEquals(e, r);
  }
  @Test
  public void testApplyCubeFoldRank3() {
    var a = Cell.of(3, 2, 3).iota();
    var r = a.apply(ADD.fold(3));
    var e = Cell.of(2, 3).reshape(Cell.of(18, 21, 24, 27, 30, 33));
    assertEquals(e, r);
  }
  @Test
  public void testApplyCubeFoldRank3Big() {
    var a = Cell.of(34, 2, 3).iota();
    var r = a.apply(ADD.fold(3));
    var e = Cell.of(2, 3).reshape(Cell.of(3366, 3400, 3434, 3468, 3502, 3536));
    assertEquals(e, r);
  }

  @Test
  public void testApplyVectorFoldTerms() {
    var a = Cell.of(1, 2, 3);
    var r = a.apply(ADD.fold(DIV, COUNT));
    assertEquals(Cell.of(2), r);
  }

  @Test
  public void testApplyVectorFoldTermsRank1() {
    var a = Cell.of(1, 2, 3);
    var r = a.apply(ADD.fold(1, DIV, COUNT));
    assertEquals(Cell.of(2), r);
  }


  private static Stream<Monads> allMonads() { return Arrays.stream(Monads.values()); }
  private static Stream<Dyads> allDyads() { return Arrays.stream(Dyads.values()); }
  private static Stream<Dyads> allAssocDyads() { return Stream.of(ADD, SUB, MUL, MIN, MAX, AND, OR, COUNT); }
  private static Stream<Cell> allCells() {
    var cell = Cell.of(rangeClosed(1, 4*5*6).toArray());
    return Stream.of(Cell.of(4), Cell.of(4, 5), Cell.of(4, 5, 6)).map(c -> c.reshape(cell));
  }

  private static Stream<Arguments> provideCellsAndMonads() {
    return allMonads().flatMap(monads -> allCells().map(cell -> Arguments.of(cell, monads)));
  }
  private static Stream<Arguments> provideCellsAndDyads() {
    return allDyads().flatMap(dyads -> allCells().flatMap(left -> allCells().map(right -> Arguments.of(left, dyads, right))));
  }
  private static Stream<Arguments> provideCellsAndFolds() {
    var cells = allCells().toList();
    return range(0, cells.size()).boxed().flatMap(dim -> allAssocDyads().flatMap(dyads -> range(1, dim + 1).boxed().map(rank -> Arguments.of(cells.get(dim), dyads.fold(rank)))));
  }
  private static Stream<Arguments> provideCellsAndFoldTerms() {
    var cells = allCells().toList();
    return range(0, cells.size()).boxed().flatMap(dim -> allAssocDyads().flatMap(dyads -> range(1, dim + 1).boxed().map(rank -> Arguments.of(cells.get(dim), dyads.fold(rank, /*MUL*/ DIV, COUNT)))));
  }

  @ParameterizedTest
  @MethodSource("provideCellsAndMonads")
  public void applyMonad(Cell a, Monads monads) {
    var r = a.apply(monads);
    assertNotNull(r);
  }
  @ParameterizedTest
  @MethodSource("provideCellsAndDyads")
  public void applyDyad(Cell left, Dyads dyads, Cell right) {
    Supplier<Cell> supplier = () -> left.apply(dyads, right);

    if (Arrays.equals(left.dims(), right.dims())) {
      var r = supplier.get();
      assertNotNull(r);
    } else {
      assertThrows(java.lang.IllegalArgumentException.class, supplier::get);
    }
  }
  @ParameterizedTest
  @MethodSource("provideCellsAndFolds")
  public void applyFold(Cell a, Fold fold) {
    var r = a.apply(fold);
    assertNotNull(r);
  }
  @ParameterizedTest
  @MethodSource("provideCellsAndFoldTerms")
  public void applyFoldTerms(Cell a, Fold fold) {
    var r = a.apply(fold);
    assertNotNull(r);
  }


  @Test
  public void iota() {
    var a = Cell.of(4, 3).iota();
    assertEquals("""
        [ 0,  1,  2
          3,  4,  5
          6,  7,  8
          9, 10, 11]\
        """, a.toString());
  }

  @Test
  public void reshape() {
    var a = Cell.of(1, 2, 3, 4, 5, 6);
    var r = Cell.of(2, 3).reshape(a);
    assertEquals("""
        [ 1,  2,  3
          4,  5,  6]\
        """, r.toString());
  }
}