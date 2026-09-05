package xyz.spigotrce.gyalang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.Program;

class CompilerTest {

  @Test void parseEmptySourceReturnsEmptyProgram() {
    Compiler compiler = new Compiler("", "test.glg");
    Program program = compiler.parse();
    assertTrue(program.statements().isEmpty());
  }

  @Test void sourceAndFilenameAreStored() {
    Compiler compiler = new Compiler("""
        class Main:
            def main():
                pass""", "main.glg");
    assertEquals("""
        class Main:
            def main():
                pass""", compiler.getSource());
    assertEquals("main.glg", compiler.getFilename());
  }

  @Test void compileProducesMainAndRuntimeClasses() {
    Compiler compiler = new Compiler(
        """
            class Main:
                def main():
                    print("hi")
            """, "hello.glg");
    Map<String, byte[]> classes = compiler.compile();
    assertTrue(classes.containsKey("gyalang/generated/Main"));
    assertTrue(classes.containsKey("xyz/spigotrce/gyalang/runtime/Builtins"));
  }
}
