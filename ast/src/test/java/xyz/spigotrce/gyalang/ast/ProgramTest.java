package xyz.spigotrce.gyalang.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProgramTest {

  @Test
  void emptyProgram() {
    Program program = new Program(List.of());
    assertTrue(program.statements().isEmpty());
  }

  @Test
  void programWithStatements() {
    Stmt stmt =
        new ExprStmt(new CallExpr(new Identifier("print"), List.of(new StringLiteral("hello"))));
    Program program = new Program(List.of(stmt));
    assertEquals(1, program.statements().size());
    assertSame(stmt, program.statements().getFirst());
  }

  @Test
  void statementsAreImmutable() {
    Program program =
        new Program(
            List.of(
                new ExprStmt(
                    new CallExpr(new Identifier("print"), List.of(new StringLiteral("a"))))));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            program
                .statements()
                .add(
                    new ExprStmt(
                        new CallExpr(new Identifier("print"), List.of(new StringLiteral("b"))))));
  }
}
