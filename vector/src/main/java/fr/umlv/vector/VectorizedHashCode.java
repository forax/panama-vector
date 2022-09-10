package fr.umlv.vector;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;

//FIXME
/*
public class VectorizedHashCode {
  record Data(String s, int i1, int i2, int i3, int i4) {

    public int hashCode() {
      return i1 ^ i2 ^ i3 ^ i4;
    }

    public int hashCode2z() {
      //var zero = IntVector.zero(IntVector.SPECIES_64);
      //var zero = IntVector.broadcast(IntVector.SPECIES_64, 0);
      //var zero = (IntVector)IntVector.SPECIES_64.broadcast(0);
      var zero = (IntVector)IntVector.SPECIES_64.zero();
      var v1 = zero.withLane(0, i1).withLane(1, i3);
      var v2 = zero.withLane(0, i2).withLane(1, i4);
      var result = v1.lanewise(VectorOperators.XOR, v2);
      return result.lane(0) ^ result.lane(1);
    }


    public int hashCode2y() {
      var zero = (IntVector)IntVector.SPECIES_64.zero();
      var v1 = zero.withLane(0, i1).withLane(1, i3);
      var v2 = zero.withLane(0, i2).withLane(1, i4);
      var result = v1.lanewise(VectorOperators.XOR, v2);
      var acc = result.lane(0);
      for(var i = 1; i < IntVector.SPECIES_64.length(); i++) {
        acc = acc ^ result.lane(i);
      }
      return acc;
    }

    public int hashCode2d() {
      var v1 = IntVector.fromArray(IntVector.SPECIES_64, new int[] { i1, i3 }, 0);
      var v2 = IntVector.fromArray(IntVector.SPECIES_64, new int[] { i2, i4 }, 0);
      var result = v1.lanewise(VectorOperators.XOR, v2);
      return result.lane(0) ^ result.lane(1);
    }

    public int hashCode2() {
      var v1 = IntVector.fromValues(IntVector.SPECIES_64, i1, i3);
      var v2 = IntVector.fromValues(IntVector.SPECIES_64, i2, i4);
      var result = v1.lanewise(VectorOperators.XOR, v2);
      return result.lane(0) ^ result.lane(1);
    }

    public int hashCode3() {
      var vector = IntVector.fromValues(IntVector.SPECIES_128, i1, i2, i3, i4);
      return vector.reduceLanes(VectorOperators.XOR);
    }

    public int hashCode3b() {
      var zero = (IntVector)IntVector.SPECIES_128.zero();
      var v = zero.withLane(0, i1).withLane(1, i2).withLane(2, i3).withLane(3, i4);
      var v1 = (IntVector)v.reinterpretShape(IntVector.SPECIES_64, 0);
      var v2 = (IntVector)v.reinterpretShape(IntVector.SPECIES_64, 1);
      var result = v1.lanewise(VectorOperators.XOR, v2);
      return result.lane(0) ^ result.lane(1);
    }

    public int hashCode4() {
      var v1 = IntVector.fromValues(IntVector.SPECIES_64, i1, i3);
      var v2 = IntVector.fromValues(IntVector.SPECIES_64, i2, i4);
      var result = v1.lanewise(VectorOperators.XOR, v2);
      return result.reduceLanes(VectorOperators.XOR);
    }
  }

  record DataWithDouble(String s, double d, int i, byte b) {

    public int hashCode() {
      var l = Double.doubleToLongBits(d);
      return ((int)((l >>> 32) ^ l)) ^ i;
    }

    public int hashCode2() {
      var l = Double.doubleToLongBits(d);
      var vector = IntVector.fromValues(IntVector.SPECIES_128, (int)(l >>> 32), (int)l, i, b);
      return vector.reduceLanes(VectorOperators.XOR);
    }
  }

  public static void main(String[] args){
    //var data = new Data("", 3.4, 5, (byte) 3);
    var data = new Data("", 1, 3, 6, 9);
    System.out.println(data.hashCode());
    System.out.println(data.hashCode2());
  }
}
 */
