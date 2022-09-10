package fr.umlv.vector;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

public class OdeToJ {
  record A(
      //boolean tbox,      // boxed ?
      int rank,         // rank [0-3]
      int depth0,       // depth (vector)
      int depth1,       // depth (matrix)
      int depth2,       // depth (brick)
      int[] pointer    // array data (either int[] or Object[])
  ) {
    public static A integer(int value) {
      return new A(/*false,*/ 0, 0, 0, 0, new int[] { value });
    }
  }

  // memmove
  private static void mv(int[] dest, int dstOffset, int[] source, int srcOffset, int n) {
    for (int i = 0; i < n; i++) {  //FIXME
      dest[i + dstOffset] = source[i + srcOffset];
    }
  }

  // tabular dimension from rank
  private static int tr(int rank, int depth0, int depth1, int depth2) {
    return switch(rank) {  //FIXME
      case 0 -> 1;
      case 1 -> depth0;
      case 2 -> depth0 * depth1;
      case 3 -> depth0 * depth1 * depth2;
      default -> throw new IndexOutOfBoundsException(rank);
    };
  }

  // --- definition ---
  // --- monadic

  // identity
  public static A id(A w) {
    return w;
  }

  // size: size([4,5,6]) -> 3
  public static A size(A w) {
    return A.integer((w.rank == 0)? 1: w.depth0);
  }

  // iota: iota(4) -> [0, 1, 2, 3]
  public static A iota(A w) {
    int n = w.pointer[0];
    int[] p = new int[n];
    for (int i = 0; i < n; i++) {  // FIXME
      p[i] = i;      // fill array from 0 to n-1.
    }
    return new A(/*false,*/ 1, n, 0, 0, p);
  }

  // box: box([1, 2, 3]) -> <[1, 2, 3]
  //public static A box(A w) {
  //  return new A(/*true,*/ 0, 0, 0, 0, new Object[] { w });
  //}
  public static A box(A w) {
    throw new UnsupportedOperationException();
  }

  // shape of the array: sha([1, 3, 4,
  //                          5, 6, 7]) -> [3, 2]
  public static A sha(A w) {
    return new A(/*false,*/ 1, w.rank, 0, 0, switch(w.rank) {
      case 0 -> new int[0];
      case 1 -> new int[] { w.depth0 };
      case 2 -> new int[] { w.depth0, w.depth1 };
      case 3 -> new int[] { w.depth0, w.depth1, w.depth2 };
      default -> throw new AssertionError();
    });
  }

  // --- diadic

  // plus: plus([1,2,3], [4,5,6]) -> [5,7,9]
  public static A plus(A a, A w) {
    int n = tr(w.rank, w.depth0, w.depth1, w.depth2);
    int[] p = new int[n];
    for (int i = 0; i < n; i++) {
      p[i] = a.pointer[i] + w.pointer[i]; // sum elements pairwise
    }
    return new A(/*false,*/ w.rank, w.depth0, w.depth1, w.depth2, p);
  }

  // from: from(1, [4, 5, 6]) -> 5
  public static A from(A a, A w) {
    int n = tr(w.rank - 1, w.depth1, w.depth2, 0);
    int index = a.pointer[0];
    int[] p = new int[n];
    mv(p, 0, w.pointer, n * index, n);
    return new A(/*w.tbox,*/ w.rank - 1, w.depth1, w.depth2, 0, p);
  }

  // cat: cat([1,2,3], [4,5,6]) -> [1,2,3,4,5,6]
  public static A cat(A a, A w) {
    int an = tr(a.rank, a.depth0, a.depth1, a.depth2);
    int wn = tr(a.rank, a.depth0, a.depth1, a.depth2);
    int n = an + wn;
    int[] p = new int[n];
    mv(p, 0, a.pointer, 0, an);
    mv(p, an, (int[])w.pointer, 0, wn);
    return new A(/*w.tbox,*/ 1, n, 0, 0, p);
  }

  public static A find(A a, A w) {
    throw new UnsupportedOperationException();
  }

  // reshape: rsh([2, 3], [1, 2,    [1, 2, 3,
  //                       3, 4] ->  4, 1, 2]
  public static A rsh(A a, A w) {
    int r = a.rank != 0 ? a.depth0: 1;
    int length = a.pointer.length;
    int n = tr(r, length <= 0? a.pointer[0]: 0, length <= 1? a.pointer[1]: 0, length <= 2? a.pointer[2]: 0);
    int wn = tr(w.rank, w.depth0, w.depth1, w.depth2);

    int m = Math.min(wn, n);
    int[] p = new int[n];
    mv(p, 0, w.pointer, 0, m);
    if (n != m) {
      mv(p, m, p, 0, n);
    }
    return new A(/*w.tbox,*/ r, length <= 0? a.pointer[0]: 0, length <= 1? a.pointer[1]: 0, length <= 2? a.pointer[2]: 0, p);
  }


  // --- parser ---

  private static final String VERB_TERMINALS = "+{~<#,";

  // for 0..9 - return array with one number inside, otherwise return null.
  private static A noun(char c) {
    if (c < '0' || c > '9') {
      return null;
    }
    return A.integer(c -'0');
  }

  // for verbs ("functions") return their index+1 in the table VT,
  // otherwise return null.
  private static int verb(char c) {
    for (int i = 0; i < VERB_TERMINALS.length();) {
      if (VERB_TERMINALS.charAt(i++) == c) {
        return i; // side effect (i++) is done before !
      }
    }
    return 0;
  }

  // tokenizer: returns an array of Objects
  // Integer        0 -> end
  // Integer [1 .. 6] -> verb,
  // A(value)         -> literal
  // Character [a..z] -> variable
  private static Object[] wd(String s) {
    int n = s.length();
    Object[] tokens = new Object[n + 1]; // allocate a token per char + 1
    for (int i = 0; i < n; i++) { // for each char
      A a = noun(s.charAt(i));
      if (a != null) {
        tokens[i] = a;            // one-digit number literals
        continue;
      }
      int v = verb(s.charAt(i));
      if (v != 0) {
        tokens[i] = v;            // verbs
        continue;
      }
      tokens[i] = s.charAt(i);    // one-letter variables a..z
    }
    tokens[n] = 0; // 0-terminate and return tokens
    return tokens;
  }


  // --- eval ---

  private static final List<BinaryOperator<A>> VERB_DIADIC = Arrays.asList(
      //    +             {             ~             <     #            ,
      null, OdeToJ::plus, OdeToJ::from, OdeToJ::find, null, OdeToJ::rsh, OdeToJ::cat);
  private static final List<UnaryOperator<A>> VERB_MONADIC = Arrays.asList(
      //    +           {             ~             <            #            ,
      null, OdeToJ::id, OdeToJ::size, OdeToJ::iota, OdeToJ::box, OdeToJ::sha, null);

  private static final A STORAGE[] = new A[26];   // global variables 'a'..'z'

  private static A ex(Object[] array, int e) {
    Object a = array[e];                                      // take the leftmost token
    if (a instanceof Character v && v >= 'a' && v <= 'z') {   // if a is a variable
      if (array[e + 1].equals('=')) {                         // ..assignment:
        return STORAGE[v - 'a'] = ex(array, e + 2);         // evaluate, store and return
      }
      a = STORAGE[v - 'a'];                                   // else: load variable
    }
    // "a" now contains the value, or a verb symbol
    if (a instanceof Integer i && i < 'a') {                  // if a verb, it must be monadic
      return VERB_MONADIC.get(i).apply(ex(array, e + 1));
    }
    if (array[e + 1] instanceof Integer i && i != 0) {        // if something follows this token -
                                                              // it must be a dyadic verb
      return VERB_DIADIC.get(i).apply((A)a, ex(array, e+2));
    }
    return (A)a;                                             // else, return noun `a` as is.
  }


  // --- repl ---

  private static void pi(int i) {
    System.out.printf("%d ", i);
  }
  private static void pr(A w) {
    int r = w.rank;
    int n = tr(r, w.depth0, w.depth1, w.depth2);
    if (r >= 1) pi(w.depth0);
    if (r >= 2) pi(w.depth1);
    if (r >= 3) pi(w.depth2);
    if (r != 0) System.out.println();
    for(int i =0; i < n; i++) {
      pi(w.pointer[i]);
    }
    System.out.println();
  }

  public static void main(String[] args) throws IOException {
    var console = System.console();
    if (console == null) {
      throw new IllegalStateException("no console attached");
    }
    String line;
    while((line = console.readLine("    ")) != null) {  // until end of input:
      try {
        var tokens = wd(line);                       // tokenize input
        var result = ex(tokens, 0);                // evaluate words
        pr(result);                                  // print result
      } catch(RuntimeException e) {
        var location = Optional.of(e.getStackTrace()).filter(s -> s.length >= 1).map(s -> s[0]).map(s -> " in " + s.getMethodName() + "() at " + s.getFileName() + ":" + s.getLineNumber());
        System.err.println("| " + e.getMessage() + location.orElse(""));
      }
    }
    System.out.println();
  }
}
