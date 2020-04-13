package fr.umlv.jruntime;

import static java.util.Objects.requireNonNull;
import static java.util.stream.IntStream.range;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorOperators.Associative;
import jdk.incubator.vector.VectorOperators.Binary;
import jdk.incubator.vector.VectorOperators.Unary;
import jdk.incubator.vector.VectorSpecies;

public final class Cell {
  private /*sealed*/ interface Rank {
    int[] depths();
    default int elements() { return Arrays.stream(depths()).reduce(1, (a, b) -> a * b); }

    // inlined by hand, see Cell#apply(Fold)
    //Cell fold(Cell self, int rank, Dyad dyad);

    record Vector(int column)  implements Rank {
      @Override
      public int[] depths() { return new int[] { column }; }

      public Cell fold(Cell self, int rank, Dyad dyad) {
        return foldValue(self, dyad);
      }
      private static Cell foldValue(Cell self, Dyad dyad) {
        var acc = BACKEND.foldValue(dyad, self.data);
        return new Cell(Rank.vector(1), new int[] { acc });
      }
    }
    record Matrix(int row, int column) implements Rank {
      @Override
      public int[] depths() { return new int[] { row, column }; }

      public Cell fold(Cell self, int rank, Dyad dyad) {
        return switch(rank) {
          case 1 -> foldVectorRow(self, dyad, row, column);
          case 2, -1 -> foldVectorColumn(self, dyad, row, column);
          default -> throw new IllegalArgumentException("invalid rank " + rank);
        };
      }
      private static Cell foldVectorRow(Cell self, Dyad dyad, int rowCount, int columnCount) {
        var data = new int[rowCount];
        BACKEND.foldVectorRow(dyad, data, self.data, rowCount, columnCount);
        return new Cell(Rank.vector(rowCount), data);
      }
      private static Cell foldVectorColumn(Cell self, Dyad dyad, int rowCount, int columnCount) {
        var data = new int[columnCount];
        BACKEND.foldVectorColumn(dyad, data, self.data, rowCount, columnCount);
        return new Cell(Rank.vector(columnCount), data);
      }
    }
    record Cube(int plane, int row, int column) implements Rank {
      @Override
      public int[] depths() { return new int[] {plane, row, column}; }

      public Cell fold(Cell self, int rank, Dyad dyad) {
        return switch(rank) {
          case 1 -> foldMatrixRow(self, dyad, plane, row, column);
          case 2 -> foldMatrixColumn(self, dyad, plane, row, column);
          case 3, -1 -> foldMatrixPlane(self, dyad, plane, row, column);
          default -> throw new IllegalArgumentException("invalid rank " + rank);
        };
      }
      private static Cell foldMatrixRow(Cell self, Dyad dyad, int planeCount, int rowCount, int columnCount) {
        var data = new int[planeCount * rowCount];
        var matrixLength = rowCount * columnCount;
        for(var k = 0; k < planeCount; k++) {
          BACKEND.foldVectorRow(dyad, data, k * rowCount, self.data, k * matrixLength, rowCount, columnCount);
        }
        return new Cell(Rank.matrix(planeCount, rowCount), data);
      }
      private static Cell foldMatrixColumn(Cell self, Dyad dyad, int planeCount, int rowCount, int columnCount) {
        var data = new int[planeCount * columnCount];
        var matrixLength = rowCount * columnCount;
        for(var k = 0; k < planeCount; k++) {
          BACKEND.foldVectorColumn(dyad, data, k * columnCount, self.data, k * matrixLength, rowCount, columnCount);
        }
        return new Cell(Rank.matrix(planeCount, columnCount), data);
      }
      private static Cell foldMatrixPlane(Cell self, Dyad dyad, int planeCount, int rowCount, int columnCount) {
        var data = BACKEND.foldMatrixPlane(dyad, self.data, planeCount, rowCount, columnCount);
        return new Cell(Rank.matrix(rowCount, columnCount), data);
      }
    }

    static Rank vector(int column) { return new Vector(column); }
    static Rank matrix(int row, int column) { return new Matrix(row, column); }
    static Rank cube(int row, int column, int plane) { return new Cube(row, column, plane); }
    static Rank of(int[] depths) {
      Arrays.stream(depths).forEach(Cell::requirePositive);
      return switch(depths.length) {
        case 1 -> vector(depths[0]);
        case 2 -> matrix(depths[0], depths[1]);
        case 3 -> cube(depths[0], depths[1], depths[2]);
        default -> throw new IllegalArgumentException("not more than 3 values");
      };
    }
  }

  private final Rank rank;
  private final int[] data;

  private Cell(Rank rank, int[] data) {
    this.rank = rank;
    this.data = data;
  }

  public static Cell of(int... data) {
    var newData = Arrays.copyOf(data, data.length);
    return new Cell(Rank.vector(newData.length), newData);
  }

  private static void requirePositive(int i) {
    if (i <= 0) {
      throw new IllegalArgumentException("value " + i + " is negative or null");
    }
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Cell cell && rank.equals(cell.rank) && Arrays.equals(data, cell.data);
  }

  @Override
  public int hashCode() {
    return rank.hashCode() ^ Arrays.hashCode(data);
  }

  @Override
  public String toString() {
    //System.err.println("rank: " + rank);
    //System.err.println("data: " + Arrays.toString(data));
    var builder = new StringBuilder();
    var depths = rank.depths();
    int length0, length1, length2;
    switch(depths.length) {
      case 1 -> { length0 = depths[0]; length1 = 1; length2 = 1; }
      case 2 -> { length0 = depths[1]; length1 = depths[0]; length2 = 1; }
      case 3 -> { length0 = depths[2]; length1 = depths[1]; length2 = depths[0]; }
      default -> throw new AssertionError();
    }
    var index = 0;
    var sep2 = "[";
    for(var i = 0; i < length2; i++) {
      builder.append(sep2);
      var sep1 = "";
      for(var j = 0; j < length1; j++) {
        builder.append(sep1);
        var sep0 = "";
        for(var k = 0; k < length0; k++) {
          builder.append(sep0).append(String.format("%2d", data[index++]));
          sep0 = ", ";
        }
        sep1 = "\n ";
      }
      sep2="\n\n ";
    }
    return builder.append("]").toString();
  }

  public interface Monad extends IntUnaryOperator {
    static Monad of(IntUnaryOperator op) {
      return op::applyAsInt;
    }
  }

  public static /*inline*/ class Fold {
    private final int rank;
    private final Fold left;
    private final Dyad dyad;
    private final Fold right;

    private Fold(int rank, Fold left, Dyad dyad, Fold right) {
      if (rank < -1 || rank > 3) {
        throw new IllegalArgumentException("invalid rank " + rank);
      }
      this.rank = rank;
      this.left = left;
      this.dyad = requireNonNull(dyad);
      this.right = right;
    }
    private boolean foldVerbs() {
      return left != null && right != null;
    }
  }

  public interface Dyad extends IntBinaryOperator {
    int zero();

    default Fold fold() {
      return new Fold(-1, null, this, null);
    }
    default Fold fold(int rank) {
      return new Fold(rank, null, this, null);
    }
    default Fold fold(Dyad op, Dyad right) {
      return new Fold(-1, fold(), op, right.fold());
    }
    default Fold fold(int rank, Dyad op, Dyad right) {
      return new Fold(rank, fold(rank), op, right.fold(rank));
    }

    static Dyad of(int zero, IntBinaryOperator op) {
      requireNonNull(op);
      return new Dyad() {
        @Override
        public int zero() {
          return zero;
        }

        @Override
        public int applyAsInt(int value1, int value2) {
          return op.applyAsInt(value1, value2);
        }
      };
    }
  }

  public enum Monads implements Monad {
    ZOMO(x -> x == 0? 0: -1),
    NEG(x -> -x),
    ABS(Math::abs),
    NOT(x -> ~x),
    ;

    private final IntUnaryOperator op;

    Monads(IntUnaryOperator op) {
      this.op = op;
    }

    @Override
    public int applyAsInt(int value) {
      return op.applyAsInt(value);
    }
  }

  public enum Dyads implements Dyad {
    ADD(0, Integer::sum),
    SUB(0, (acc, b) -> acc - b),
    MUL(1, (acc, b) -> acc * b),
    DIV(1, (acc, b) -> acc / b),

    MAX(Integer.MIN_VALUE, Math::max),
    MIN(Integer.MAX_VALUE, Math::min),

    AND(0xFFFFFFFF, (acc, b) -> acc & b),
    AND_NOT(0xFFFFFFFF, (acc, b) -> acc & ~b),
    OR(0, (acc, b) -> acc | b),
    XOR(0, (acc, b) -> acc ^ b),

    COUNT(0, (acc, value) -> acc + 1),
    ;

    private final int zero;
    private final IntBinaryOperator op;

    Dyads(int zero, IntBinaryOperator op) {
      this.zero = zero;
      this.op = op;
    }

    @Override
    public int zero() {
      return zero;
    }

    @Override
    public int applyAsInt(int value1, int value2) {
      return op.applyAsInt(value1, value2);
    }
  }

  public Cell apply(Monad monad) {
    requireNonNull(monad);
    return new Cell(rank, BACKEND.applyUnary(monad, data));
  }

  public Cell apply(Dyad dyad, Cell cell) {
    requireNonNull(dyad);
    if (!rank.equals(cell.rank)) {  // implicit nullcheck
      throw new IllegalArgumentException("not the same depths " + rank + " " + cell.rank);
    }
    return new Cell(rank, BACKEND.applyBinary(dyad, data, cell.data));
  }

  public Cell apply(Fold fold) {
    if (fold.foldVerbs()) { // implicit nullcheck
      return apply(fold.left).apply(fold.dyad, apply(fold.right));
    }
    // hand inlined
    if (rank instanceof Rank.Vector vector) {
      return vector.fold(this, fold.rank, fold.dyad);
    }
    if (rank instanceof Rank.Matrix matrix) {
      return matrix.fold(this, fold.rank, fold.dyad);
    }
    if (rank instanceof Rank.Cube cube) {
      return cube.fold(this, fold.rank, fold.dyad);
    }
    throw new AssertionError();
  }


  public Cell iota() {
    var newRank = Rank.of(data);
    var newData = range(0, newRank.elements()).toArray();
    return new Cell(newRank, newData);
  }

  public Cell reshape(Cell cell) {
    var newRank = Rank.of(data);
    var elements = newRank.elements();
    var newData = new int[elements];
    for(var i = 0; i < elements; i++) {
      newData[i] = cell.data[i % cell.data.length];
    }
    return new Cell(newRank, newData);
  }

  // --- backend implementation ---
  private static final Backend BACKEND;
  static {
    var enableVectorizedBackend = Boolean.parseBoolean(System.getProperty("fr.umlv.jruntime.vectorized", "true"));
    Backend backend;
    if (enableVectorizedBackend) {
      try {
        Class.forName("jdk.incubator.vector.IntVector");
        backend = new VectorizedBackend();
      } catch(ClassNotFoundException e) {
        backend = new ClassicBackend();
      }
    } else {
      backend = new ClassicBackend();
    }
    BACKEND = backend;
  }

  public static String backendVersion() { return BACKEND.toString(); }

  private static abstract class Backend {
    private int[] applyUnary(Monad monad, int[] src) {
      if (monad instanceof Monads monads) {
        return switch(monads) {
          case ZOMO -> applyUnaryZOMO(src);
          case NEG -> applyUnaryNEG(src);
          case ABS -> applyUnaryABS(src);
          case NOT -> applyUnaryNOT(src);
        };
      }
      return ClassicBackend.applyUnaryGeneric(src, monad);
    }
    private int[] applyBinary(Dyad dyad, int[] src1, int[] src2) {
      if (dyad instanceof Dyads dyads) {
        return switch(dyads) {
          case ADD -> applyBinaryADD(src1, src2);
          case SUB -> applyBinarySUB(src1, src2);
          case MUL -> applyBinaryMUL(src1, src2);
          case DIV -> applyBinaryDIV(src1, src2);
          case MAX -> applyBinaryMAX(src1, src2);
          case MIN -> applyBinaryMIN(src1, src2);
          case AND -> applyBinaryAND(src1, src2);
          case AND_NOT -> applyBinaryAND_NOT(src1, src2);
          case OR -> applyBinaryOR(src1, src2);
          case XOR -> applyBinaryXOR(src1, src2);
          case COUNT -> applyBinaryCOUNT(src1, src2);
        };
      }
      return ClassicBackend.applyBinaryGeneric(src1, src2, dyad);
    }

    private int foldValue(Dyad dyad, int[] src) {
      if (dyad instanceof Dyads dyads) {
        return switch(dyads) {
          case ADD -> foldValueADD(src);
          case SUB -> foldValueSUB(src);
          case MUL -> foldValueMUL(src);
          case DIV -> foldValueDIV(src);
          case MAX -> foldValueMAX(src);
          case MIN -> foldValueMIN(src);
          case AND -> foldValueAND(src);
          case AND_NOT -> foldValueAND_NOT(src);
          case OR -> foldValueOR(src);
          case XOR -> foldValueXOR(src);
          case COUNT -> foldValueCOUNT(src);
        };
      }
      return ClassicBackend.foldValueGeneric(src, dyad.zero(), dyad);
    }
    private void foldVectorColumn(Dyad dyad, int[] dst, int[] src, int rowCount, int columnCount) {
      if (dyad instanceof Dyads dyads) {
        switch(dyads) {
          case ADD -> { foldVectorColumnADD(dst, src, rowCount, columnCount); return; }
          case SUB -> { foldVectorColumnSUB(dst, src, rowCount, columnCount); return; }
          case MUL -> { foldVectorColumnMUL(dst, src, rowCount, columnCount); return; }
          case DIV -> { foldVectorColumnDIV(dst, src, rowCount, columnCount); return; }
          case MAX -> { foldVectorColumnMAX(dst, src, rowCount, columnCount); return; }
          case MIN -> { foldVectorColumnMIN(dst, src, rowCount, columnCount); return; }
          case AND -> { foldVectorColumnAND(dst, src, rowCount, columnCount); return; }
          case AND_NOT -> { foldVectorColumnAND_NOT(dst, src, rowCount, columnCount); return; }
          case OR -> { foldVectorColumnOR(dst, src, rowCount, columnCount); return; }
          case XOR -> { foldVectorColumnXOR(dst, src, rowCount, columnCount); return; }
          case COUNT -> { foldVectorColumnCOUNT(dst, src, rowCount, columnCount); return; }
          default -> throw new AssertionError();
        }
      }
      ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, dyad.zero(), dyad);
    }
    private void foldVectorColumn(Dyad dyad, int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) {
      if (dyad instanceof Dyads dyads) {
        switch(dyads) {
          case ADD -> { foldVectorColumnADD(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case SUB -> { foldVectorColumnSUB(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case MUL -> { foldVectorColumnMUL(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case DIV -> { foldVectorColumnDIV(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case MAX -> { foldVectorColumnMAX(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case MIN -> { foldVectorColumnMIN(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case AND -> { foldVectorColumnAND(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case AND_NOT -> { foldVectorColumnAND_NOT(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case OR -> { foldVectorColumnOR(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case XOR -> { foldVectorColumnXOR(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case COUNT -> { foldVectorColumnCOUNT(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          default -> throw new AssertionError();
        }
      }
      ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, dyad.zero(), dyad);
    }

    private void foldVectorRow(Dyad dyad, int[] dst, int[] src, int rowCount, int columnCount) {
      if (dyad instanceof Dyads dyads) {
        switch(dyads) {
          case ADD -> { foldVectorRowADD(dst, src, rowCount, columnCount); return; }
          case SUB -> { foldVectorRowSUB(dst, src, rowCount, columnCount); return; }
          case MUL -> { foldVectorRowMUL(dst, src, rowCount, columnCount); return; }
          case DIV -> { foldVectorRowDIV(dst, src, rowCount, columnCount); return; }
          case MAX -> { foldVectorRowMAX(dst, src, rowCount, columnCount); return; }
          case MIN -> { foldVectorRowMIN(dst, src, rowCount, columnCount); return; }
          case AND -> { foldVectorRowAND(dst, src, rowCount, columnCount); return; }
          case AND_NOT -> { foldVectorRowAND_NOT(dst, src, rowCount, columnCount); return; }
          case OR -> { foldVectorRowOR(dst, src, rowCount, columnCount); return; }
          case XOR -> { foldVectorRowXOR(dst, src, rowCount, columnCount); return; }
          case COUNT -> { foldVectorRowCOUNT(dst, src, rowCount, columnCount); return; }
          default -> throw new AssertionError();
        }
      }
      ClassicBackend.foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, dyad.zero(), dyad);
    }
    private void foldVectorRow(Dyad dyad, int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) {
      if (dyad instanceof Dyads dyads) {
        switch(dyads) {
          case ADD -> { foldVectorRowADD(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case SUB -> { foldVectorRowSUB(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case MUL -> { foldVectorRowMUL(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case DIV -> { foldVectorRowDIV(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case MAX -> { foldVectorRowMAX(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case MIN -> { foldVectorRowMIN(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case AND -> { foldVectorRowAND(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case AND_NOT -> { foldVectorRowAND_NOT(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case OR -> { foldVectorRowOR(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case XOR -> { foldVectorRowXOR(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          case COUNT -> { foldVectorRowCOUNT(dst, dstOffset, src, srcOffset, rowCount, columnCount); return; }
          default -> throw new AssertionError();
        }
      }
      ClassicBackend.foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, dyad.zero(), dyad);
    }

    private int[] foldMatrixPlane(Dyad dyad, int[] src, int planeCount, int rowCount, int columnCount) {
      if (dyad instanceof Dyads dyads) {
        return switch(dyads) {
          case ADD -> foldMatrixPlaneADD(src, planeCount, rowCount, columnCount);
          case SUB -> foldMatrixPlaneSUB(src, planeCount, rowCount, columnCount);
          case MUL -> foldMatrixPlaneMUL(src, planeCount, rowCount, columnCount);
          case DIV -> foldMatrixPlaneDIV(src, planeCount, rowCount, columnCount);
          case MAX -> foldMatrixPlaneMAX(src, planeCount, rowCount, columnCount);
          case MIN -> foldMatrixPlaneMIN(src, planeCount, rowCount, columnCount);
          case AND -> foldMatrixPlaneAND(src, planeCount, rowCount, columnCount);
          case AND_NOT -> foldMatrixPlaneAND_NOT(src, planeCount, rowCount, columnCount);
          case OR -> foldMatrixPlaneOR(src, planeCount, rowCount, columnCount);
          case XOR -> foldMatrixPlaneXOR(src, planeCount, rowCount, columnCount);
          case COUNT -> foldMatrixPlaneCOUNT(src, planeCount, rowCount, columnCount);
        };
      }
      return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, dyad.zero(), dyad);
    }

    abstract int[] applyUnaryZOMO(int[] src);
    abstract int[] applyUnaryNEG(int[] src);
    abstract int[] applyUnaryABS(int[] src);
    abstract int[] applyUnaryNOT(int[] src);

    abstract int[] applyBinaryADD(int[] src1, int[] src2);
    abstract int[] applyBinarySUB(int[] src1, int[] src2);
    abstract int[] applyBinaryMUL(int[] src1, int[] src2);
    abstract int[] applyBinaryDIV(int[] src1, int[] src2);
    abstract int[] applyBinaryMAX(int[] src1, int[] src2);
    abstract int[] applyBinaryMIN(int[] src1, int[] src2);
    abstract int[] applyBinaryAND(int[] src1, int[] src2);
    abstract int[] applyBinaryAND_NOT(int[] src1, int[] src2);
    abstract int[] applyBinaryOR(int[] src1, int[] src2);
    abstract int[] applyBinaryXOR(int[] src1, int[] src2);
    abstract int[] applyBinaryCOUNT(int[] src1, int[] src2);

    abstract int foldValueADD(int[] src);
    abstract int foldValueSUB(int[] src);
    abstract int foldValueMUL(int[] src);
    abstract int foldValueDIV(int[] src);
    abstract int foldValueMAX(int[] src);
    abstract int foldValueMIN(int[] src);
    abstract int foldValueAND(int[] src);
    abstract int foldValueAND_NOT(int[] src);
    abstract int foldValueOR(int[] src);
    abstract int foldValueXOR(int[] src);
    abstract int foldValueCOUNT(int[] src);

    final void foldVectorColumnADD(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 0, Integer::sum); }
    final void foldVectorColumnSUB(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a - b); }
    final void foldVectorColumnMUL(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 1, (a, b) -> a * b); }
    final void foldVectorColumnDIV(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 1, (a, b) -> a / b); }
    final void foldVectorColumnMAX(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, Integer.MIN_VALUE, Math::max); }
    final void foldVectorColumnMIN(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, Integer.MAX_VALUE, Math::min); }
    final void foldVectorColumnAND(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & b); }
    final void foldVectorColumnAND_NOT(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & ~b); }
    final void foldVectorColumnOR(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a | b); }
    final void foldVectorColumnXOR(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a ^ b); }
    final void foldVectorColumnCOUNT(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a + 1); }

    final void foldVectorColumnADD(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, Integer::sum); }
    final void foldVectorColumnSUB(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a - b); }
    final void foldVectorColumnMUL(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 1, (a, b) -> a * b); }
    final void foldVectorColumnDIV(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 1, (a, b) -> a / b); }
    final void foldVectorColumnMAX(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, Integer.MIN_VALUE, Math::max); }
    final void foldVectorColumnMIN(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, Integer.MAX_VALUE, Math::min); }
    final void foldVectorColumnAND(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & b); }
    final void foldVectorColumnAND_NOT(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & ~b); }
    final void foldVectorColumnOR(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a | b); }
    final void foldVectorColumnXOR(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a ^ b); }
    final void foldVectorColumnCOUNT(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorColumnGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a + 1); }

    abstract void foldVectorRowADD(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowSUB(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowMUL(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowDIV(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowMAX(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowMIN(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowAND(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowAND_NOT(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowOR(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowXOR(int[] dst, int[] src, int rowCount, int columnCount);
    abstract void foldVectorRowCOUNT(int[] dst, int[] src, int rowCount, int columnCount);

    abstract void foldVectorRowADD(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowSUB(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowMUL(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowDIV(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowMAX(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowMIN(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowAND(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowAND_NOT(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowOR(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowXOR(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);
    abstract void foldVectorRowCOUNT(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount);

    final int[] foldMatrixPlaneADD(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 0, Integer::sum); }
    final int[] foldMatrixPlaneSUB(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 0, (a, b) -> a - b); }
    final int[] foldMatrixPlaneMUL(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 1, (a, b) -> a * b); }
    final int[] foldMatrixPlaneDIV(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 1, (a, b) -> a / b); }
    final int[] foldMatrixPlaneMAX(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, Integer.MIN_VALUE, Math::max); }
    final int[] foldMatrixPlaneMIN(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, Integer.MAX_VALUE, Math::min); }
    final int[] foldMatrixPlaneAND(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & b); }
    final int[] foldMatrixPlaneAND_NOT(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & ~b); }
    final int[] foldMatrixPlaneOR(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 0, (a, b) -> a | b); }
    final int[] foldMatrixPlaneXOR(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 0, (a, b) -> a ^ b); }
    final int[] foldMatrixPlaneCOUNT(int[] src, int planeCount, int rowCount, int columnCount) { return ClassicBackend.foldMatrixPlaneGeneric(src, planeCount, rowCount, columnCount, 0, (a, b) -> a + 1); }
  }

  private static final class ClassicBackend extends Backend {
    @Override
    public String toString() {
      return "Classic";
    }

    int[] applyUnaryZOMO(int[] src) { return applyUnaryGeneric(src, x -> x == 0? 0: -1);  }
    int[] applyUnaryNEG(int[] src) { return applyUnaryGeneric(src, x -> -x);  }
    int[] applyUnaryABS(int[] src) { return applyUnaryGeneric(src, Math::abs);  }
    int[] applyUnaryNOT(int[] src) { return applyUnaryGeneric(src, x -> ~x);  }

    int[] applyBinaryADD(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, Integer::sum); }
    int[] applyBinarySUB(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, (a, b) -> a - b); }
    int[] applyBinaryMUL(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, (a, b) -> a * b); }
    int[] applyBinaryDIV(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, (a, b) -> a / b); }
    int[] applyBinaryMAX(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, Math::max); }
    int[] applyBinaryMIN(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, Math::min); }
    int[] applyBinaryAND(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, (a, b) -> a & b); }
    int[] applyBinaryAND_NOT(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, (a, b) -> a & ~b); }
    int[] applyBinaryOR(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, (a, b) -> a | b); }
    int[] applyBinaryXOR(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, (a, b) -> a ^ b); }
    int[] applyBinaryCOUNT(int[] src1, int[] src2) { return applyBinaryGeneric(src1, src2, (a, b) -> a + 1); }

    int foldValueADD(int[] src) { return foldValueGeneric(src, 0, Integer::sum); }
    int foldValueSUB(int[] src) { return foldValueGeneric(src, 0, (a, b) -> a - b); }
    int foldValueMUL(int[] src) { return foldValueGeneric(src, 1, (a, b) -> a * b); }
    int foldValueDIV(int[] src) { return foldValueGeneric(src, 1, (a, b) -> a / b); }
    int foldValueMAX(int[] src) { return foldValueGeneric(src, Integer.MIN_VALUE, Math::max); }
    int foldValueMIN(int[] src) { return foldValueGeneric(src, Integer.MAX_VALUE, Math::min); }
    int foldValueAND(int[] src) { return foldValueGeneric(src, 0xFFFFFFFF, (a, b) -> a & b); }
    int foldValueAND_NOT(int[] src) { return foldValueGeneric(src, 0xFFFFFFFF, (a, b) -> a & ~b); }
    int foldValueOR(int[] src) { return foldValueGeneric(src, 0, (a, b) -> a | b); }
    int foldValueXOR(int[] src) { return foldValueGeneric(src, 0, (a, b) -> a ^ b); }
    int foldValueCOUNT(int[] src) { return foldValueGeneric(src, 0, (a, b) -> a + 1); }

    void foldVectorRowADD(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 0, Integer::sum); }
    void foldVectorRowSUB(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a - b); }
    void foldVectorRowMUL(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 1, (a, b) -> a * b); }
    void foldVectorRowDIV(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 1, (a, b) -> a / b); }
    void foldVectorRowMAX(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, Integer.MIN_VALUE, Math::max); }
    void foldVectorRowMIN(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, Integer.MAX_VALUE, Math::min); }
    void foldVectorRowAND(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & b); }
    void foldVectorRowAND_NOT(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & ~b); }
    void foldVectorRowOR(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a | b); }
    void foldVectorRowXOR(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a ^ b); }
    void foldVectorRowCOUNT(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a + 1); }

    void foldVectorRowADD(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, Integer::sum); }
    void foldVectorRowSUB(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a - b); }
    void foldVectorRowMUL(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 1, (a, b) -> a * b); }
    void foldVectorRowDIV(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 1, (a, b) -> a / b); }
    void foldVectorRowMAX(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, Integer.MIN_VALUE, Math::max); }
    void foldVectorRowMIN(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, Integer.MAX_VALUE, Math::min); }
    void foldVectorRowAND(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & b); }
    void foldVectorRowAND_NOT(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & ~b); }
    void foldVectorRowOR(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a | b); }
    void foldVectorRowXOR(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a ^ b); }
    void foldVectorRowCOUNT(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a + 1); }

    private static int[] applyUnaryGeneric(int[] src, IntUnaryOperator op) {
      var data = new int[src.length];
      for(var i = 0; i < src.length; i++) {
        data[i] = op.applyAsInt(src[i]);
      }
      return data;
    }
    private static int[] applyBinaryGeneric(int[] src1, int[] src2, IntBinaryOperator op) {
      var data = new int[src1.length];
      for(var i = 0; i < data.length; i++) {
        data[i] = op.applyAsInt(src1[i], src2[i]);
      }
      return data;
    }
    private static int foldValueGeneric(int[] src, int zero, IntBinaryOperator op) {
      var acc = zero;
      for(var i = 0; i < src.length; i++) {
        acc = op.applyAsInt(acc, src[i]);
      }
      return acc;
    }
    private static void foldVectorColumnGeneric(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount, int zero, IntBinaryOperator op) {
      for(var j = 0; j < columnCount; j++) {
        var acc = zero;
        for(var i = 0; i < rowCount; i++) {
          acc = op.applyAsInt(acc, src[srcOffset + i * columnCount + j]);
        }
        dst[dstOffset + j] = acc;
      }
    }
    private static void foldVectorRowGeneric(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount, int zero, IntBinaryOperator op) {
      var index = 0;
      for(var j = 0; j < rowCount; j++) {
        var acc = zero;
        for(var i = 0; i < columnCount; i++) {
          acc = op.applyAsInt(acc, src[srcOffset + index++]);
        }
        dst[dstOffset + j] = acc;
      }
    }
    private static int[] foldMatrixPlaneGeneric(int[] src, int planeCount, int rowCount, int columnCount, int zero, IntBinaryOperator op) {
      var matrixSize = rowCount * columnCount;
      var data = new int[matrixSize];
      for(var index = 0; index < data.length; index++) {
        var acc = zero;
        for(var k = 0; k < planeCount; k++) {
          acc = op.applyAsInt(acc, src[index + k * matrixSize]);
        }
        data[index] = acc;
      }
      return data;
    }
  }

  private static final class VectorizedBackend extends Backend {
    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    @Override
    public String toString() {
      return "Vectorized - lanes: " + SPECIES.length() + "  shape: " + SPECIES.vectorShape();
    }

    int[] applyUnaryZOMO(int[] src) { return applyUnaryTemplate(src, x -> x == 0? 0: -1, VectorOperators.ZOMO);  }
    int[] applyUnaryNEG(int[] src) { return applyUnaryTemplate(src, x -> -x, VectorOperators.NEG);  }
    int[] applyUnaryABS(int[] src) { return applyUnaryTemplate(src, Math::abs, VectorOperators.ABS);  }
    int[] applyUnaryNOT(int[] src) { return applyUnaryTemplate(src, x -> ~x, VectorOperators.NOT);  }

    int[] applyBinaryADD(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, Integer::sum, VectorOperators.ADD); }
    int[] applyBinarySUB(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, (a, b) -> a - b, VectorOperators.SUB); }
    int[] applyBinaryMUL(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, (a, b) -> a * b, VectorOperators.MUL); }
    int[] applyBinaryDIV(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, (a, b) -> a / b, VectorOperators.DIV); }
    int[] applyBinaryMAX(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, Math::max, VectorOperators.MAX); }
    int[] applyBinaryMIN(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, Math::min, VectorOperators.MIN); }
    int[] applyBinaryAND(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, (a, b) -> a & b, VectorOperators.AND); }
    int[] applyBinaryAND_NOT(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, (a, b) -> a & ~b, VectorOperators.AND_NOT); }
    int[] applyBinaryOR(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, (a, b) -> a & ~b, VectorOperators.OR); }
    int[] applyBinaryXOR(int[] src1, int[] src2) { return applyBinaryTemplate(src1, src2, (a, b) -> a ^b, VectorOperators.XOR); }
    int[] applyBinaryCOUNT(int[] src1, int[] src2) { return ClassicBackend.applyBinaryGeneric(src1, src2, (a, b) -> a + 1); }

    int foldValueADD(int[] src) { return foldValueTemplate(src, 0, Integer::sum, VectorOperators.ADD); }
    int foldValueSUB(int[] src) { return foldValueTemplate(src, 0, (a, b) -> a - b, VectorOperators.SUB); }
    int foldValueMUL(int[] src) { return foldValueTemplate(src, 1, (a, b) -> a * b, VectorOperators.MUL); }
    int foldValueDIV(int[] src) { return foldValueTemplate(src, 1, (a, b) -> a / b, VectorOperators.DIV); }
    int foldValueMAX(int[] src) { return foldValueTemplate(src, Integer.MIN_VALUE, Math::max, VectorOperators.MAX); }
    int foldValueMIN(int[] src) { return foldValueTemplate(src, Integer.MAX_VALUE, Math::min, VectorOperators.MIN); }
    int foldValueAND(int[] src) { return foldValueTemplate(src, 0xFFFFFFFF, (a, b) -> a & b, VectorOperators.AND); }
    int foldValueAND_NOT(int[] src) { return foldValueTemplate(src, 0xFFFFFFFF, (a, b) -> a & ~b, VectorOperators.AND_NOT); }
    int foldValueOR(int[] src) { return foldValueTemplate(src, 0, (a, b) -> a | b, VectorOperators.OR); }
    int foldValueXOR(int[] src) { return foldValueTemplate(src, 0, (a, b) -> a ^ b, VectorOperators.XOR); }
    int foldValueCOUNT(int[] src) { return ClassicBackend.foldValueGeneric(src, 0, (a, b) -> a + 1); }

    void foldVectorRowADD(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, 0, Integer::sum, VectorOperators.ADD); }
    void foldVectorRowSUB(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a - b, VectorOperators.SUB); }
    void foldVectorRowMUL(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, 1, (a, b) -> a * b, VectorOperators.MUL); }
    void foldVectorRowDIV(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, 1, (a, b) -> a / b, VectorOperators.DIV); }
    void foldVectorRowMAX(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, Integer.MIN_VALUE, Math::max, VectorOperators.MAX); }
    void foldVectorRowMIN(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, Integer.MAX_VALUE, Math::min, VectorOperators.MIN); }
    void foldVectorRowAND(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & b, VectorOperators.AND); }
    void foldVectorRowAND_NOT(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & ~b, VectorOperators.AND_NOT); }
    void foldVectorRowOR(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a | b, VectorOperators.OR); }
    void foldVectorRowXOR(int[] dst, int[] src, int rowCount, int columnCount) { foldVectorRowTemplate(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a ^ b, VectorOperators.XOR); }
    void foldVectorRowCOUNT(int[] dst, int[] src, int rowCount, int columnCount) { ClassicBackend.foldVectorRowGeneric(dst, 0, src, 0, rowCount, columnCount, 0, (a, b) -> a + 1); }

    void foldVectorRowADD(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, Integer::sum, VectorOperators.ADD); }
    void foldVectorRowSUB(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a - b, VectorOperators.SUB); }
    void foldVectorRowMUL(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, 1, (a, b) -> a * b, VectorOperators.MUL); }
    void foldVectorRowDIV(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, 1, (a, b) -> a / b, VectorOperators.DIV); }
    void foldVectorRowMAX(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, Integer.MIN_VALUE, Math::max, VectorOperators.MAX); }
    void foldVectorRowMIN(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, Integer.MAX_VALUE, Math::min, VectorOperators.MIN); }
    void foldVectorRowAND(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & b, VectorOperators.AND); }
    void foldVectorRowAND_NOT(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0xFFFFFFFF, (a, b) -> a & ~b, VectorOperators.AND_NOT); }
    void foldVectorRowOR(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a | b, VectorOperators.OR); }
    void foldVectorRowXOR(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { foldVectorRowTemplate(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a ^ b, VectorOperators.XOR); }
    void foldVectorRowCOUNT(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount) { ClassicBackend.foldVectorRowGeneric(dst, dstOffset, src, srcOffset, rowCount, columnCount, 0, (a, b) -> a + 1); }


    private static int[] applyUnaryTemplate(int[] src, IntUnaryOperator op, Unary unary) {
      var data = new int[src.length];
      var i = 0;
      var limit = src.length - (src.length % SPECIES.length());
      for (; i < limit; i += SPECIES.length()) {
        var v = IntVector.fromArray(SPECIES, src, i);
        var vr = v.lanewise(unary);                              // apply lanewise
        vr.intoArray(data, i);
      }
      for (; i < src.length; i++) {                              // post loop
        data[i] = op.applyAsInt(src[i]);
      }
      return data;
    }
    private static int[] applyBinaryTemplate(int[] src1, int[] src2, IntBinaryOperator op, Binary binary) {
      var data = new int[src1.length];
      var i = 0;
      var limit = src1.length - (src1.length % SPECIES.length());
      for (; i < limit; i += SPECIES.length()) {
        var v1 = IntVector.fromArray(SPECIES, src1, i);
        var v2 = IntVector.fromArray(SPECIES, src2, i);
        var vr = v1.lanewise(binary, v2);                       // apply lanewise
        vr.intoArray(data, i);
      }
      for (; i < src1.length; i++) {                            // post loop
        data[i] = op.applyAsInt(src1[i], src2[i]);
      }
      return data;
    }
    private static int foldValueTemplate(int[] src, int zero, IntBinaryOperator op, Associative assoc) {
      var acc = IntVector.broadcast(SPECIES, zero);
      var i = 0;
      var limit = src.length - (src.length % SPECIES.length());
      for (; i < limit; i += SPECIES.length()) {                // reduce lanewise
        var vector = IntVector.fromArray(SPECIES, src, i);
        acc = acc.lanewise(assoc, vector) ;
      }
      var result = acc.reduceLanes(assoc);                      // reduce the lane
      for (; i < src.length; i++) {                             // post loop
        result = op.applyAsInt(result, src[i]);
      }
      return result;
    }
    private static int foldValueTemplate(int[] src, int zero, IntBinaryOperator op, Binary binary) {
      var acc = IntVector.broadcast(SPECIES, zero);
      var i = 0;
      var limit = src.length - (src.length % SPECIES.length());
      for (; i < limit; i += SPECIES.length()) {                // reduce lanewise
        var vector = IntVector.fromArray(SPECIES, src, i);
        acc = acc.lanewise(binary, vector) ;
      }
      var result = zero;
      for (; i < src.length; i++) {                             // post loop
        result = op.applyAsInt(result, src[i]);
      }
      for(var value: acc.toIntArray()) {                        // reduce the lane
        result = op.applyAsInt(result, value);
      }
      return result;
    }
    private static void foldVectorRowTemplate(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount, int zero, IntBinaryOperator op, Associative assoc) {
      var index = srcOffset;
      for(var j = 0; j < rowCount; j++) {
        var acc = IntVector.broadcast(SPECIES, zero);
        var i = 0;
        var limit = columnCount - (columnCount % SPECIES.length());
        for(; i < limit; i += SPECIES.length()) {             // reduce lane wise
          var vector = IntVector.fromArray(SPECIES, src, index + i);
          acc = acc.lanewise(assoc, vector) ;
        }
        var result = acc.reduceLanes(assoc);                  // reduce the lane
        for (; i < columnCount; i++) {                        // post loop
          result = op.applyAsInt(result, src[index + i]);
        }
        dst[dstOffset + j] = result;
        index += columnCount;
      }
    }

    private static void foldVectorRowTemplate(int[] dst, int dstOffset, int[] src, int srcOffset, int rowCount, int columnCount, int zero, IntBinaryOperator op, Binary binary) {
      var index = srcOffset;
      for(var j = 0; j < rowCount; j++) {
        var acc = IntVector.broadcast(SPECIES, zero);
        var i = 0;
        var limit = columnCount - (columnCount % SPECIES.length());
        for(; i < limit; i += SPECIES.length()) {             // reduce lane wise
          var vector = IntVector.fromArray(SPECIES, src, index + i);
          acc = acc.lanewise(binary, vector) ;
        }
        var result = zero;
        for (; i < columnCount; i++) {                       // post loop
          result = op.applyAsInt(result, src[index + i]);
        }
        for(var value: acc.toIntArray()) {                  // reduce the lane
          result = op.applyAsInt(result, value);
        }
        dst[dstOffset + j] = result;
        index += columnCount;
      }
    }
  }
}
