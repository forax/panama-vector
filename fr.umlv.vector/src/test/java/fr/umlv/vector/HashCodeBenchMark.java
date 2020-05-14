package fr.umlv.vector;

import fr.umlv.vector.VectorizedHashCode.Data;
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


// /path/to/jdk-15-vector/bin/java --module-path target/test/artifact:deps -m fr.umlv.vector/fr.umlv.vector.HashCodeBenchMark
@SuppressWarnings("static-method")
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = { "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableVectorSupport"/*, "-XX:-UseSuperWord"*/})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class HashCodeBenchMark {
  private VectorizedHashCode.Data data = new Data("", 1, 3, 6, 9);

  //@Benchmark
  public int classic_hashCode() {
    return data.hashCode();
  }

  @Benchmark
  public int vectorized_hashCode() {
    return data.hashCode2();
  }

  public static void main(String[] args) throws RunnerException {
    var opt = new OptionsBuilder().include(HashCodeBenchMark.class.getName()).build();
    new Runner(opt).run();
  }
}


