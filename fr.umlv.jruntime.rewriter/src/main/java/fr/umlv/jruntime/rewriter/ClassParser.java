package fr.umlv.jruntime.rewriter;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ClassParser {
  private static final String[] ENTRY_NAMES = {
      null, "UTF8_ENTRY", null, "INTEGER_ENTRY", "FLOAT_ENTRY", "LONG_ENTRY", "DOUBLE_ENTRY",
      "CLASS_ENTRY", "STRING_ENTRY", "FIELDREF_ENTRY", "CLASS_METHODREF_ENTRY", "INTERFACE_METHODREF_ENTRY", "NAME_AND_TYPE_ENTRY",
      null, null,
      "METHOD_HANDLE_ENTRY", "METHOD_TYPE_ENTRY", "CONSTANT_DYNAMIC_ENTRY", "INVOKE_DYNAMIC_ENTRY", "MODULE_ENTRY", "PACKAGE_ENTRY"
  };

  private static final int UTF8_ENTRY = 1;
  private static final int INTEGER_ENTRY = 3;
  private static final int FLOAT_ENTRY = 4;
  private static final int LONG_ENTRY = 5;
  private static final int DOUBLE_ENTRY= 6;
  private static final int CLASS_ENTRY = 7;
  private static final int STRING_ENTRY = 8;
  private static final int FIELDREF_ENTRY = 9;
  private static final int CLASS_METHODREF_ENTRY = 10;
  private static final int INTERFACE_METHODREF_ENTRY = 11;
  private static final int NAME_AND_TYPE_ENTRY = 12;
  private static final int METHOD_HANDLE_ENTRY = 15;
  private static final int METHOD_TYPE_ENTRY = 16;
  private static final int CONSTANT_DYNAMIC_ENTRY = 17;
  private static final int INVOKE_DYNAMIC_ENTRY = 18;
  private static final int MODULE_ENTRY = 19;
  private static final int PACKAGE_ENTRY = 20;

  private static int readUnsignedShort(byte[] bytes, int index) {
    return ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
  }

  private static String readUtf8(byte[] bytes, int offset, int utf8Length) {
    var buffer = new char[utf8Length];
    var strLength = 0;
    for (var i = 0; i < utf8Length;) {
      var firstByte = bytes[offset + i++];
      if ((firstByte & 0x80) == 0) {   // type 1
        buffer[strLength++] = (char) (firstByte & 0x7F);
        continue;
      }
      if ((firstByte & 0xE0) == 0xC0) { // type 2
        buffer[strLength++] =
            (char) (((firstByte & 0x1F) << 6) + (bytes[offset + i++] & 0x3F));
        continue;
      }
      // type 3
      buffer[strLength++] =
          (char)
              (((firstByte & 0xF) << 12)
                  + ((bytes[offset + i++] & 0x3F) << 6)
                  + (bytes[offset + i++] & 0x3F));
    }
    return new String(buffer, 0, strLength);
  }

  private static int[] scanForHoleIndexes(String[] holes, byte[] bytes) {
    var holeMap = range(0, holes.length).boxed().collect(toMap(i -> holes[i], i -> i));

    var size = readUnsignedShort(bytes, 8);
    var utf8s = new int[size];
    var strings = new int[size];

    var byteOffset = 10;
    for (var index = 1; index < size; index++) {
      var entryTag = bytes[byteOffset];
      //System.out.println(index + ": " + ENTRY_NAMES[entryTag] + " " + entryTag);

      byteOffset += switch (entryTag) {
        case CLASS_ENTRY,
            METHOD_TYPE_ENTRY,
            PACKAGE_ENTRY,
            MODULE_ENTRY -> 3;
        case METHOD_HANDLE_ENTRY -> 4;
        case FIELDREF_ENTRY,
            CLASS_METHODREF_ENTRY,
            INTERFACE_METHODREF_ENTRY,
            INTEGER_ENTRY,
            FLOAT_ENTRY,
            NAME_AND_TYPE_ENTRY,
            CONSTANT_DYNAMIC_ENTRY,
            INVOKE_DYNAMIC_ENTRY -> 5;
        case LONG_ENTRY,
            DOUBLE_ENTRY ->  {
          index++;
          yield 9;
        }
        case UTF8_ENTRY -> {
          var utf8Length = readUnsignedShort(bytes, byteOffset + 1);
          var string = readUtf8(bytes, byteOffset + 3, utf8Length);
          System.out.println("  string = " + string);
          utf8s[index] = holeMap.getOrDefault(string, -1);
          yield 3 + utf8Length;
        }
        case STRING_ENTRY -> {
          var utf8Index = readUnsignedShort(bytes, byteOffset + 1);
          System.out.println("  utf8Index = " + utf8Index);
          strings[index] = utf8Index;
          yield 3;
        }
        default -> throw new AssertionError("invalid entry tag " + entryTag);
      };
    }

    var holeIndexes = new int[holes.length];
    for(var i = 0; i < size; i++) {
      var utf8Index = strings[i];
      if (utf8Index != 0) {
        var holeIndex = utf8s[utf8Index];
        if (holeIndex != -1) {
          holeIndexes[holeIndex] = i;
        }
      }
    }
    return holeIndexes;
  }

  public static void main(String[] args) throws IOException {
    var path = Path.of("Snippet.class");
    var data = Files.readAllBytes(path);

    var indexes = scanForHoleIndexes(new String[] { "HOLE1", "HOLE2", "HOLE3" }, data);
    System.out.println(Arrays.toString(indexes));
  }
}
