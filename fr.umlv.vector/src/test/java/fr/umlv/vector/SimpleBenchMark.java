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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;



// /path/to/jdk-15-vector/bin/java --module-path target/test/artifact:deps -m fr.umlv.vector/fr.umlv.vector.SimpleBenchMark
@SuppressWarnings("static-method")
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = { "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableVectorSupport" })
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class SimpleBenchMark {
  private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

  private int[] array = new Random(0).ints(1_000_000, 0,1_000_000).toArray();

//  @Benchmark
//  public void sum_loop(Blackhole blackhole) {
//    var sum = 0;
//    for (var i = 0; i < array.length; i++) {
//      sum += array[i];
//    }
//    blackhole.consume(sum);
//  }
//
//  @Benchmark
//  public void sum_vector(Blackhole blackhole) {
//    var sum = 0;
//    for (var i = 0; i < array.length; i += SPECIES.length()) {
//      var mask = SPECIES.indexInRange(i, array.length);
//      var vector = IntVector.fromArray(SPECIES, array, i, mask);
//      var result = vector.reduceLanes(VectorOperators.ADD, mask);
//      sum += result;
//    }
//    blackhole.consume(sum);
//  }

  @Benchmark
  public void max_loop(Blackhole blackhole) {
    var max = Integer.MIN_VALUE;
    for (var i = 0; i < array.length; i++) {
      max = Math.max(max, array[i]);
    }
    blackhole.consume(max);
  }

  @Benchmark
  public void max_vector(Blackhole blackhole) {
    var max = Integer.MIN_VALUE;
    for (var i = 0; i < array.length; i += SPECIES.length()) {
      var mask = SPECIES.indexInRange(i, array.length);
      var vector = IntVector.fromArray(SPECIES, array, i, mask);
      var result = vector.reduceLanes(VectorOperators.MAX, mask);
      max = Math.max(max, result);
    }
    blackhole.consume(max);
  }

  public static void main(String[] args) throws RunnerException {
    var opt = new OptionsBuilder().include(SimpleBenchMark.class.getName()).build();
    new Runner(opt).run();
  }
}


