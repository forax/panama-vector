package fr.umlv.vector;

import static fr.umlv.jruntime.Cell.Dyads.ADD;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import fr.umlv.jruntime.Cell;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;


// /path/to/jdk-15-vector/bin/java --enable-preview --module-path target/test/artifact:deps  -Dfr.umlv.jruntime.vectorized=true  -m fr.umlv.vector/fr.umlv.vector.CellBenchMark
@SuppressWarnings("static-method")
@Warmup(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class CellBenchMark {
  private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

  private int[] array = new Random(0).ints(1_000_000, 0,100).toArray();
  private Cell cell = Cell.of(array);

  @Benchmark
  public int add_loop() {
    var add = 0;
    for (var i = 0; i < array.length; i++) {
      add += array[i];
    }
    return add;
  }

  @Benchmark
  public int add_vector_lanewise() {
    var acc = IntVector.zero(SPECIES);
    var i = 0;
    var limit = array.length - (array.length % SPECIES.length());
    for (; i < limit; i += SPECIES.length()) {
      var vector = IntVector.fromArray(SPECIES, array, i);
      acc = acc.add(vector);
    }
    var sum = acc.reduceLanes(VectorOperators.ADD);
    for (; i < array.length; i++) {
      sum += array[i];
    }
    return sum;
  }

  @Benchmark
  public Cell add_cell() {
    return cell.apply(ADD.fold());
  }

  public static void main(String[] args) throws RunnerException {
    var opt = new OptionsBuilder().include(CellBenchMark.class.getName()).build();
    new Runner(opt).run();
  }
}


