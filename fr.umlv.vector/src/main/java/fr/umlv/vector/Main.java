package fr.umlv.vector;

import static java.util.stream.IntStream.range;

import java.util.Arrays;
import java.util.stream.IntStream;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorOperators.Associative;
import jdk.incubator.vector.VectorSpecies;

public class Main {
  private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

  static void vectorMultiply(int[] array1, int[] array2, int[] result) {
    // It is assumed array arguments are of the same size
    for (var i = 0; i < array1.length; i += SPECIES.length()) {
      var mask = SPECIES.indexInRange(i, array1.length);
      var vector1 = IntVector.fromArray(SPECIES, array1, i, mask);
      var vector2 = IntVector.fromArray(SPECIES, array2, i, mask);
      var vectorResult = vector1.mul(vector2);
      vectorResult.intoArray(result, i, mask);
    }
  }

  public static int max(int[] array) {
    var max = array[0];
    for (var i = 0; i < array.length; i += SPECIES.length()) {
      var mask = SPECIES.indexInRange(i, array.length);
      var vector = IntVector.fromArray(SPECIES, array, i, mask);
      var result = vector.reduceLanes(VectorOperators.MAX, mask);
      max = Math.max(max, result);
    }
    return max;
  }

  public static int sum(int[] array) {
    var sum = array[0];
    for (var i = 0; i < array.length; i += SPECIES.length()) {
      var mask = SPECIES.indexInRange(i, array.length);
      var vector = IntVector.fromArray(SPECIES, array, i, mask);
      var result = vector.reduceLanes(VectorOperators.ADD, mask);
      sum += result;
    }
    return sum;
  }

  public static void main(String[] args) {
    System.out.println(SPECIES);

    var array = range(0, 10_000).toArray();
    System.out.println("sum: " + sum(array));
    System.out.println("max: " + max(array));

    var result = new int[array.length];
    vectorMultiply(array, array, result);
    System.out.println("sum: " + sum(result));
  }
}
