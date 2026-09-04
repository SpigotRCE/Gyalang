package xyz.spigotrce.gyalang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.Program;

class CompilerTest {

  @Test void parseEmptySourceReturnsEmptyProgram() {
    Compiler compiler = new Compiler("", "test.glg");
    Program program = compiler.parse();
    assertTrue(program.statements().isEmpty());
  }

  @Test void sourceAndFilenameAreStored() {
    Compiler compiler = new Compiler("x = 1", "main.glg");
    assertEquals("x = 1", compiler.getSource());
    assertEquals("main.glg", compiler.getFilename());
  }
}
