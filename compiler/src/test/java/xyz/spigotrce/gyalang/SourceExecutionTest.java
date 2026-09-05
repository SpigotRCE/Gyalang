package xyz.spigotrce.gyalang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles real Gylang source (as it would be written in a .glg file) through
 * the full lexer -> parser -> codegen pipeline, then runs the output with the
 * real JVM.
 */
class SourceExecutionTest {

  @TempDir Path tempDir;

  @Test void simplePrintAndMath() throws Exception {
    String source = """
        class Main:
            def main():
                x = 5
                y = 3
                print(x)
                print(x + y)
        """;
    assertEquals("5\n8", executeSource(source, "basic.glg"));
  }

  private String executeSource(String source, String filename) throws Exception {
    Compiler compiler = new Compiler(source, filename);
    Map<String, byte[]> classes = compiler.compile();

    Path out = tempDir.resolve("classes");
    for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
      Path classFile = out.resolve(entry.getKey() + ".class");
      Files.createDirectories(classFile.getParent());
      Files.write(classFile, entry.getValue());
    }

    String javaHome = System.getProperty("java.home");
    String className = "gyalang.generated.Main";

    Process process = new ProcessBuilder(javaHome + "/bin/java", "-cp", out.toString(), className).redirectErrorStream(true).start();

    String output;
    try (InputStream is = process.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      byte[] buf = new byte[4096];
      int n;
      while ((n = is.read(buf)) != -1) {
        bos.write(buf, 0, n);
      }
      output = bos.toString();
    }

    int exitCode = process.waitFor();
    assertEquals(0, exitCode, "JVM exited with " + exitCode + ":\n" + output);
    return output.replace("\r\n", "\n").trim();
  }

  @Test void ifElse() throws Exception {
    String source = """
        class Main:
            def main():
                x = 5
                if x > 3:
                    print("big")
                else:
                    print("small")
        """;
    assertEquals("big", executeSource(source, "branch.glg"));
  }

  @Test void whileLoop() throws Exception {
    String source = """
        class Main:
            def main():
                i = 0
                while i < 3:
                    print(i)
                    i = i + 1
        """;
    assertEquals("""
        0
        1
        2""", executeSource(source, "loop.glg"));
  }

  @Test void forLoopOverString() throws Exception {
    String source = """
        class Main:
            def main():
                out = ""
                for ch in "abc":
                    out = out + ch
                print(out)
                count = 0
                for ch in "hello":
                    count = count + 1
                print(count)
        """;
    assertEquals("abc\n5", executeSource(source, "for.glg"));
  }

  @Test void rangeLoop() throws Exception {
    String source = """
        class Main:
            def main():
                total = 0
                for i in range(4):
                    total = total + i
                print(total)
                for i in range(2, 5):
                    print(i)
                for i in range(3, 0, -1):
                    print(i)
        """;
    assertEquals("""
        6
        2
        3
        4
        3
        2
        1""", executeSource(source, "range.glg"));
  }

  @Test void mathBuiltins() throws Exception {
    String source = """
        class Main:
            def main():
                print(abs(-5))
                print(abs(-2.5))
                print(min(3, 7))
                print(max(3, 7))
                print(round(3.6))
                print(sqrt(16.0))
        """;
    assertEquals("""
        5
        2.5
        3
        7
        4
        4.0""", executeSource(source, "math.glg"));
  }

  @Test void stringBuiltins() throws Exception {
    String source = """
        class Main:
            def main():
                print(sum(range(1, 5)))
                print(ord("A"))
                print(chr(66))
                print(len("hello"))
        """;
    assertEquals("""
        10
        65
        B
        5""", executeSource(source, "stringbuiltins.glg"));
  }

  @Test void printWithSepAndEnd() throws Exception {
    String source = """
        class Main:
            def main():
                print("Hello", "World")
                print("a", "b", "c", sep=", ")
                print("x", end="")
                print("|")
        """;
    assertEquals("""
        Hello World
        a, b, c
        x|""", executeSource(source, "printkw.glg"));
  }

  @Test void printAcceptsFileAndFlushKeywords() throws Exception {
    String source = """
        class Main:
            def main():
                print("a", "b", sep="-", file="io", flush=True)
                print("c", flush=False)
                print("d", end="")
        """;
    assertEquals("""
        a-b
        c
        d""", executeSource(source, "printfull.glg"));
  }

  @Test void methodDefaultsAndKeywords() throws Exception {
    String source = """
        class Main:
            def greet(name, greeting="Hello", punct="!"):
                print(greeting, name, sep=" ", end="")
                print(punct)
        
            def main():
                Main.greet("Alice")
                Main.greet("Bob", "Hi")
                Main.greet("Carol", greeting="Hey", punct=".")
                Main.greet("Dave", punct="?", greeting="Yo")
        """;
    assertEquals("""
        Hello Alice!
        Hi Bob!
        Hey Carol.
        Yo Dave?""", executeSource(source, "defaults.glg"));
  }

  @Test void constructorWithMethodDefaults() throws Exception {
    String source = """
        class Point:
            def __init__(self, x=0, y=0):
                self.x = x
                self.y = y
            def show(self):
                print(self.x, self.y, sep=",")
        
        class Main:
            def main():
                Point().show()
                Point(3).show()
                Point(3, 4).show()
                Point(y=7).show()
        """;
    assertEquals("""
        0,0
        3,0
        3,4
        0,7""", executeSource(source, "ctor_defaults.glg"));
  }

  @Test void methodVarargs() throws Exception {
    String source = """
        class Main:
            def show(label, *nums):
                print(label, end=":")
                for n in nums:
                    print(" ", n, sep="", end="")
                print()
        
            def main():
                Main.show("none")
                Main.show("one", 5)
                Main.show("three", 1, 2, 3)
        """;
    assertEquals("""
        none:
        one: 5
        three: 1 2 3""", executeSource(source, "varargs.glg"));
  }

  @Test void instanceMethodWithTypedParams() throws Exception {
    String source = """
        class Math:
            def add(self, a: int, b: int):
                return a + b
        
        class Main:
            def main():
                print(Math().add(40, 2))
        """;
    assertEquals("42", executeSource(source, "method.glg"));
  }

  @Test void staticMethodCall() throws Exception {
    String source = """
        class Util:
            def double(n: int):
                return n * 2
        
        class Main:
            def main():
                print(Util.double(21))
        """;
    assertEquals("42", executeSource(source, "static.glg"));
  }

  @Test void constructorWithFieldsAndMethods() throws Exception {
    String source = """
        class Counter:
            def __init__(self, start: int):
                self.count: int = start
        
            def bump(self):
                self.count = self.count + 1
        
            def get(self):
                return self.count
        
        class Main:
            def main():
                c = Counter(10)
                c.bump()
                c.bump()
                print(c.get())
        """;
    assertEquals("12", executeSource(source, "counter.glg"));
  }

  @Test void twoObjectsHaveIndependentFields() throws Exception {
    String source = """
        class Counter:
            def __init__(self, start: int):
                self.count: int = start
        
            def bump(self):
                self.count = self.count + 1
        
            def get(self):
                return self.count
        
        class Main:
            def main():
                a = Counter(0)
                b = Counter(100)
                a.bump()
                print(a.get())
                print(b.get())
        """;
    assertEquals("1\n100", executeSource(source, "independent.glg"));
  }

  @Test void builtinLenAndStr() throws Exception {
    String source = """
        class Main:
            def main():
                s = "hello"
                print(len(s))
                print(str(100))
        """;
    assertEquals("5\n100", executeSource(source, "builtins.glg"));
  }

  @Test void typedVariables() throws Exception {
    String source = """
        class Main:
            def main():
                a: int = 10
                b: float = a
                print(a)
                print(b)
        """;
    assertEquals("10\n10.0", executeSource(source, "typed.glg"));
  }

  @Test void typedVariableWithoutInitializerDefaultsToZero() throws Exception {
    String source = """
        class Main:
            def main():
                count: int
                print(count)
        """;
    assertEquals("0", executeSource(source, "typed_default.glg"));
  }

  @Test void mixedIntFloatArithmeticProducesFloat() throws Exception {
    String source = """
        class Main:
            def main():
                x = 2 + 3.5
                print(x)
        """;
    assertEquals("5.5", executeSource(source, "mixed.glg"));
  }

  @Test void floatFieldStorageWorks() throws Exception {
    String source = """
        class Bank:
            def __init__(self):
                self.balance: float = 0.0
        
            def deposit(self, amount: float):
                self.balance = self.balance + amount
        
            def get_balance(self):
                return self.balance
        
        class Main:
            def main():
                b = Bank()
                b.deposit(1.5)
                b.deposit(2.25)
                print(b.get_balance())
        """;
    assertEquals("3.75", executeSource(source, "bank.glg"));
  }

  @Test void mismatchedAssignmentIsRejected() {
    String source = """
        class Main:
            def main():
                a: int = 1
                a = "oops"
        """;
    assertThrows(IllegalStateException.class, () -> new Compiler(source, "bad.glg").compile());
  }

  @Test void readBeforeDeclarationIsRejected() {
    String source = """
        class Main:
            def main():
                print(x)
                x = 1
        """;
    assertThrows(IllegalStateException.class, () -> new Compiler(source, "undef.glg").compile());
  }

  @Test void missingEntryClassIsRejected() {
    String source = """
        class Foo:
            def main():
                print(1)
        """;
    assertThrows(IllegalStateException.class, () -> new Compiler(source, "nomain.glg").compile());
  }

  @Test void entryClassWithoutMainIsRejected() {
    String source = """
        class Main:
            def helper(self):
                print(1)
        """;
    assertThrows(IllegalStateException.class, () -> new Compiler(source, "nomethod.glg").compile());
  }
}
