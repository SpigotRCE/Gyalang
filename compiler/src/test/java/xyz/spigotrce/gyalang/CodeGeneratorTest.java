package xyz.spigotrce.gyalang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.Block;
import xyz.spigotrce.gyalang.ast.CallExpr;
import xyz.spigotrce.gyalang.ast.ClassDef;
import xyz.spigotrce.gyalang.ast.ExprStmt;
import xyz.spigotrce.gyalang.ast.FuncDef;
import xyz.spigotrce.gyalang.ast.Identifier;
import xyz.spigotrce.gyalang.ast.Param;
import xyz.spigotrce.gyalang.ast.Program;
import xyz.spigotrce.gyalang.ast.Stmt;
import xyz.spigotrce.gyalang.ast.StringLiteral;

class CodeGeneratorTest {

  @Test
  void emptyProgramProducesOnlyRuntimeClass() {
    CodeGenerator gen = new CodeGenerator("test.glg");
    Map<String, byte[]> result = gen.generateClasses(new Program(List.of()));

    assertNotNull(result);
    assertTrue(result.containsKey("xyz/spigotrce/gyalang/runtime/Builtins"));
    assertEquals(1, result.size());
  }

  @Test
  void filenameIsStored() {
    CodeGenerator gen = new CodeGenerator("output.glg");
    assertEquals("output.glg", gen.getFilename());
    assertEquals("gyalang/generated/Main", gen.getClassName());
  }

  @Test
  void classFileForPrintProgramIsProduced() {
    Program program = mainProgram(List.of(print(new StringLiteral("hi"))));
    CodeGenerator gen = new CodeGenerator("hello.glg");
    Map<String, byte[]> result = gen.generateClasses(program);

    assertTrue(result.containsKey("gyalang/generated/Main"));
    int magic =
        (result.get("gyalang/generated/Main")[0] & 0xFF) << 24
            | (result.get("gyalang/generated/Main")[1] & 0xFF) << 16
            | (result.get("gyalang/generated/Main")[2] & 0xFF) << 8
            | (result.get("gyalang/generated/Main")[3] & 0xFF);
    assertEquals(0xCAFEBABE, magic);
  }

  private static Program mainProgram(List<Stmt> body) {
    return new Program(
        List.of(
            new ClassDef(
                "Main",
                List.of(new FuncDef("main", List.of(new Param("self", "obj")), new Block(body))))));
  }

  private static ExprStmt print(StringLiteral value) {
    return new ExprStmt(new CallExpr(new Identifier("print"), List.of(value)));
  }

  @Test
  void blockBodyIsSupported() {
    Program program = mainProgram(List.of(new Block(List.of())));
    CodeGenerator gen = new CodeGenerator("block.glg");
    Map<String, byte[]> result = gen.generateClasses(program);
    assertTrue(result.containsKey("gyalang/generated/Main"));
  }
}
