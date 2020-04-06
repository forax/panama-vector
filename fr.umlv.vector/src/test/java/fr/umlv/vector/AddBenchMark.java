package fr.umlv.vector;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
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


// /path/to/jdk-15-vector/bin/java --module-path target/test/artifact:deps -m fr.umlv.vector/fr.umlv.vector.AddBenchMark
@SuppressWarnings("static-method")
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = { "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableVectorSupport", "-XX:-UseSuperWord"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class AddBenchMark {
  private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

  private int[] array = new Random(0).ints(1_000_000, 0,1_000_000).toArray();

  @Benchmark
  public int add_loop() {
    var add = 0;
    for (var i = 0; i < array.length; i++) {
      add += array[i];
    }
    return add;
  }

  @Benchmark
  public int add_vector_post_loop() {
    var sum = 0;
    var i = 0;
    var limit = array.length - (array.length % SPECIES.length());
    for (; i < limit; i += SPECIES.length()) {
      var vector = IntVector.fromArray(SPECIES, array, i);
      var result = vector.reduceLanes(VectorOperators.ADD);
      sum +=result;
    }
    for (; i < array.length; i += 1) {
      sum += array[i];
    }
    return sum;
  }

  @Benchmark
  public int add_vector_lanewise() {
    var acc = IntVector.zero(SPECIES);
    var i = 0;
    var limit = array.length - (array.length % SPECIES.length());
    for (; i < limit; i += SPECIES.length()) {
      var vector = IntVector.fromArray(SPECIES, array, i);
      acc = acc.lanewise(VectorOperators.ADD, vector) ;
    }
    var sum = acc.reduceLanes(VectorOperators.ADD);
    for (; i < array.length; i++) {
      sum += array[i];
    }
    return sum;
  }

  /*
  @Benchmark
  public int add_vector_lanewise_unrolled2() {
    var acc1 = IntVector.zero(SPECIES);
    var acc2 = IntVector.zero(SPECIES);
    var i = 0;
    var limit = array.length - (array.length % (2 * SPECIES.length()));
    for (; i < limit; i += 2 * SPECIES.length()) {
      acc1 = acc1.lanewise(VectorOperators.ADD,
          IntVector.fromArray(SPECIES, array, i + 0 * SPECIES.length()));
      acc2 = acc2.lanewise(VectorOperators.ADD,
          IntVector.fromArray(SPECIES, array, i + 1 * SPECIES.length()));
    }
    var sum = acc1.reduceLanes(VectorOperators.ADD) + acc2.reduceLanes(VectorOperators.ADD);
    for (; i < array.length; i += 1) {
      sum += array[i];
    }
    return sum;
  }*/

  /*
  @Benchmark
  public int add_vector_mask() {
    var add = 0;
    for (var i = 0; i < array.length; i += SPECIES.length()) {
      var mask = SPECIES.indexInRange(i, array.length);
      var vector = IntVector.fromArray(SPECIES, array, i, mask);
      var result = vector.reduceLanes(VectorOperators.ADD, mask);
      add += result;
    }
    return add;
  }*/

  public static void main(String[] args) throws RunnerException {
    var opt = new OptionsBuilder().include(AddBenchMark.class.getName()).build();
    new Runner(opt).run();
  }
}


