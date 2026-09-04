package xyz.spigotrce.gyalang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.Program;

class CodeGeneratorTest {

  @Test void generateEmptyProgramReturnsEmptyBytes() {
    CodeGenerator gen = new CodeGenerator("test.glg");
    byte[] result = gen.generate(new Program(java.util.List.of()));
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  @Test void filenameIsStored() {
    CodeGenerator gen = new CodeGenerator("output.glg");
    assertEquals("output.glg", gen.getFilename());
  }
}
