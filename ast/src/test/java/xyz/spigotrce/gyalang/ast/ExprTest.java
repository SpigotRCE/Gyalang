package xyz.spigotrce.gyalang.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExprTest {

  @Test
  void stringLiteral() {
    StringLiteral lit = new StringLiteral("hello world");
    assertEquals("hello world", lit.value());
  }

  @Test
  void intLiteral() {
    IntLiteral lit = new IntLiteral(42);
    assertEquals(42, lit.value());
  }

  @Test
  void floatLiteral() {
    FloatLiteral lit = new FloatLiteral(3.14);
    assertEquals(3.14, lit.value());
  }

  @Test
  void boolLiteral() {
    assertTrue(new BoolLiteral(true).value());
    assertFalse(new BoolLiteral(false).value());
  }

  @Test
  void identifier() {
    Identifier id = new Identifier("myVar");
    assertEquals("myVar", id.name());
  }

  @Test
  void binExpr() {
    Expr left = new IntLiteral(1);
    Expr right = new IntLiteral(2);
    BinExpr expr = new BinExpr(BinExpr.Op.ADD, left, right);
    assertSame(left, expr.left());
    assertSame(right, expr.right());
    assertEquals(BinExpr.Op.ADD, expr.op());
  }

  @Test
  void unaryExpr() {
    Expr operand = new IntLiteral(5);
    UnaryExpr expr = new UnaryExpr(UnaryExpr.Op.NEG, operand);
    assertSame(operand, expr.operand());
    assertEquals(UnaryExpr.Op.NEG, expr.op());
  }

  @Test
  void callExpr() {
    Expr callee = new Identifier("print");
    List<Expr> args = List.of(new StringLiteral("hi"), new IntLiteral(1));
    CallExpr call = new CallExpr(callee, args);
    assertSame(callee, call.callee());
    assertEquals(2, call.arguments().size());
  }

  @Test
  void callExprArgumentsAreImmutable() {
    CallExpr call = new CallExpr(new Identifier("f"), List.of());
    assertThrows(
        UnsupportedOperationException.class, () -> call.arguments().add(new IntLiteral(1)));
  }

  @Test
  void nestedExpressions() {
    BinExpr expr =
        new BinExpr(
            BinExpr.Op.ADD, new UnaryExpr(UnaryExpr.Op.NEG, new IntLiteral(1)), new IntLiteral(2));
    assertInstanceOf(BinExpr.class, expr);
    assertInstanceOf(UnaryExpr.class, expr.left());
    assertInstanceOf(IntLiteral.class, expr.right());
  }
}
