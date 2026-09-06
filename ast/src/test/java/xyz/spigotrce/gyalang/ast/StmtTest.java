package xyz.spigotrce.gyalang.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StmtTest {

  @Test
  void exprStmt() {
    Expr call = new CallExpr(new Identifier("f"), List.of());
    ExprStmt stmt = new ExprStmt(call);
    assertSame(call, stmt.expr());
  }

  @Test
  void printCall() {
    Expr value = new StringLiteral("hi");
    ExprStmt stmt = new ExprStmt(new CallExpr(new Identifier("print"), List.of(value)));
    CallExpr call = (CallExpr) stmt.expr();
    assertEquals("print", ((Identifier) call.callee()).name());
    assertSame(value, call.arguments().getFirst());
  }

  @Test
  void assignment() {
    Expr value = new IntLiteral(42);
    Assignment assign = new Assignment("x", value);
    assertEquals("x", assign.name());
    assertSame(value, assign.value());
  }

  @Test
  void ifStmtWithoutElse() {
    Expr cond = new BoolLiteral(true);
    Block thenBlock = new Block(List.of());
    IfStmt stmt = new IfStmt(cond, thenBlock);
    assertSame(cond, stmt.condition());
    assertSame(thenBlock, stmt.thenBlock());
    assertNull(stmt.elseBlock());
  }

  @Test
  void ifStmtWithElse() {
    Expr cond = new BoolLiteral(false);
    Block thenBlock = new Block(List.of());
    Block elseBlock = new Block(List.of());
    IfStmt stmt = new IfStmt(cond, thenBlock, elseBlock);
    assertSame(elseBlock, stmt.elseBlock());
  }

  @Test
  void whileStmt() {
    Expr cond = new BoolLiteral(true);
    Block body = new Block(List.of());
    WhileStmt stmt = new WhileStmt(cond, body);
    assertSame(cond, stmt.condition());
    assertSame(body, stmt.body());
  }

  @Test
  void blockIsImmutable() {
    ExprStmt a =
        new ExprStmt(new CallExpr(new Identifier("print"), List.of(new StringLiteral("a"))));
    ExprStmt b =
        new ExprStmt(new CallExpr(new Identifier("print"), List.of(new StringLiteral("b"))));
    Block block = new Block(List.of(a, b));
    assertEquals(2, block.statements().size());
    assertThrows(UnsupportedOperationException.class, () -> block.statements().clear());
  }

  @Test
  void returnStmtWithValue() {
    Expr value = new IntLiteral(10);
    ReturnStmt stmt = new ReturnStmt(value);
    assertSame(value, stmt.value());
  }

  @Test
  void returnStmtWithoutValue() {
    ReturnStmt stmt = new ReturnStmt();
    assertNull(stmt.value());
  }

  @Test
  void funcDef() {
    Block body = new Block(List.of(new ReturnStmt(new Identifier("x"))));
    FuncDef func =
        new FuncDef(
            "add",
            List.of(new Param("self", "obj"), new Param("a", "obj"), new Param("b", "obj")),
            body);
    assertEquals("add", func.name());
    assertEquals("self", func.parameters().getFirst().name());
    assertEquals("obj", func.parameters().getFirst().type());
    assertEquals("a", func.parameters().get(1).name());
    assertEquals("b", func.parameters().get(2).name());
    assertSame(body, func.body());
  }

  @Test
  void funcDefParametersAreImmutable() {
    FuncDef func = new FuncDef("f", List.of(new Param("x", "obj")), new Block(List.of()));
    assertThrows(
        UnsupportedOperationException.class, () -> func.parameters().add(new Param("y", "obj")));
  }

  @Test
  void paramStoresNameAndType() {
    Param param = new Param("n", "int");
    assertEquals("n", param.name());
    assertEquals("int", param.type());
  }

  @Test
  void classDef() {
    FuncDef main = new FuncDef("main", List.of(new Param("self", "obj")), new Block(List.of()));
    ClassDef clazz = new ClassDef("Main", List.of(main));
    assertEquals("Main", clazz.name());
    assertSame(main, clazz.methods().getFirst());
  }

  @Test
  void getAttr() {
    GetAttr attr = new GetAttr(new Identifier("p"), "x");
    assertEquals("x", attr.name());
    assertEquals("p", ((Identifier) attr.receiver()).name());
  }

  @Test
  void setAttr() {
    Expr value = new IntLiteral(3);
    SetAttr set = new SetAttr(new Identifier("self"), "count", value, "int");
    assertEquals("count", set.name());
    assertEquals("int", set.type());
    assertSame(value, set.value());
  }

  @Test
  void setAttrWithoutTypeAndValue() {
    SetAttr set = new SetAttr(new Identifier("self"), "count", null, null);
    assertNull(set.value());
    assertNull(set.type());
  }
}
