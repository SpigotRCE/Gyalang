package xyz.spigotrce.gyalang.ast;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StmtTest {

  @Test void printStmt() {
    Expr value = new StringLiteral("hi");
    PrintStmt stmt = new PrintStmt(value);
    assertSame(value, stmt.value());
  }

  @Test void assignment() {
    Expr value = new IntLiteral(42);
    Assignment assign = new Assignment("x", value);
    assertEquals("x", assign.name());
    assertSame(value, assign.value());
  }

  @Test void ifStmtWithoutElse() {
    Expr cond = new BoolLiteral(true);
    Block thenBlock = new Block(List.of());
    IfStmt stmt = new IfStmt(cond, thenBlock);
    assertSame(cond, stmt.condition());
    assertSame(thenBlock, stmt.thenBlock());
    assertNull(stmt.elseBlock());
  }

  @Test void ifStmtWithElse() {
    Expr cond = new BoolLiteral(false);
    Block thenBlock = new Block(List.of());
    Block elseBlock = new Block(List.of());
    IfStmt stmt = new IfStmt(cond, thenBlock, elseBlock);
    assertSame(elseBlock, stmt.elseBlock());
  }

  @Test void whileStmt() {
    Expr cond = new BoolLiteral(true);
    Block body = new Block(List.of());
    WhileStmt stmt = new WhileStmt(cond, body);
    assertSame(cond, stmt.condition());
    assertSame(body, stmt.body());
  }

  @Test void blockIsImmutable() {
    Block block = new Block(List.of(new PrintStmt(new StringLiteral("a")), new PrintStmt(new StringLiteral("b"))));
    assertEquals(2, block.statements().size());
    assertThrows(UnsupportedOperationException.class, () -> block.statements().clear());
  }

  @Test void returnStmtWithValue() {
    Expr value = new IntLiteral(10);
    ReturnStmt stmt = new ReturnStmt(value);
    assertSame(value, stmt.value());
  }

  @Test void returnStmtWithoutValue() {
    ReturnStmt stmt = new ReturnStmt();
    assertNull(stmt.value());
  }

  @Test void funcDef() {
    Block body = new Block(List.of(new ReturnStmt(new Identifier("x"))));
    FuncDef func = new FuncDef("add", List.of("a", "b"), body);
    assertEquals("add", func.name());
    assertEquals(List.of("a", "b"), func.parameters());
    assertSame(body, func.body());
  }

  @Test void funcDefParametersAreImmutable() {
    FuncDef func = new FuncDef("f", List.of("x"), new Block(List.of()));
    assertThrows(UnsupportedOperationException.class, () -> func.parameters().add("y"));
  }
}
