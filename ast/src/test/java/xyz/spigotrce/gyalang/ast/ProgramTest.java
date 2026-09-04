package xyz.spigotrce.gyalang.ast;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgramTest {

  @Test void emptyProgram() {
    Program program = new Program(List.of());
    assertTrue(program.statements().isEmpty());
  }

  @Test void programWithStatements() {
    Stmt stmt = new PrintStmt(new StringLiteral("hello"));
    Program program = new Program(List.of(stmt));
    assertEquals(1, program.statements().size());
    assertSame(stmt, program.statements().getFirst());
  }

  @Test void statementsAreImmutable() {
    Program program = new Program(List.of(new PrintStmt(new StringLiteral("a"))));
    assertThrows(UnsupportedOperationException.class, () -> program.statements().add(new PrintStmt(new StringLiteral("b"))));
  }
}
