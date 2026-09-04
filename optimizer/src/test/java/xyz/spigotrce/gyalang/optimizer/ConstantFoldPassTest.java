package xyz.spigotrce.gyalang.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.Assignment;
import xyz.spigotrce.gyalang.ast.BinExpr;
import xyz.spigotrce.gyalang.ast.BoolLiteral;
import xyz.spigotrce.gyalang.ast.Expr;
import xyz.spigotrce.gyalang.ast.FloatLiteral;
import xyz.spigotrce.gyalang.ast.Identifier;
import xyz.spigotrce.gyalang.ast.IntLiteral;
import xyz.spigotrce.gyalang.ast.PrintStmt;
import xyz.spigotrce.gyalang.ast.Program;
import xyz.spigotrce.gyalang.ast.Stmt;
import xyz.spigotrce.gyalang.ast.UnaryExpr;

class ConstantFoldPassTest {

  private final ConstantFoldPass pass = new ConstantFoldPass();

  @Test void foldsIntegerAddition() {
    Program input = new Program(List.of(new PrintStmt(new BinExpr(BinExpr.Op.ADD, new IntLiteral(2), new IntLiteral(3)))));
    Program result = pass.run(input);
    Stmt stmt = result.statements().get(0);
    assertInstanceOf(PrintStmt.class, stmt);
    Expr folded = ((PrintStmt) stmt).value();
    assertInstanceOf(IntLiteral.class, folded);
    assertEquals(5, ((IntLiteral) folded).value());
  }

  @Test void foldsNestedArithmetic() {
    // (1 + 2) * 3
    Expr expr = new BinExpr(BinExpr.Op.MUL, new BinExpr(BinExpr.Op.ADD, new IntLiteral(1), new IntLiteral(2)), new IntLiteral(3));
    Program input = new Program(List.of(new PrintStmt(expr)));
    Program result = pass.run(input);
    Expr folded = ((PrintStmt) result.statements().get(0)).value();
    assertInstanceOf(IntLiteral.class, folded);
    assertEquals(9, ((IntLiteral) folded).value());
  }

  @Test void foldsFloatAddition() {
    Expr expr = new BinExpr(BinExpr.Op.ADD, new FloatLiteral(1.5), new FloatLiteral(2.5));
    Program input = new Program(List.of(new PrintStmt(expr)));
    Program result = pass.run(input);
    Expr folded = ((PrintStmt) result.statements().get(0)).value();
    assertInstanceOf(FloatLiteral.class, folded);
    assertEquals(4.0, ((FloatLiteral) folded).value());
  }

  @Test void foldsComparisons() {
    Expr expr = new BinExpr(BinExpr.Op.LT, new IntLiteral(1), new IntLiteral(2));
    Program input = new Program(List.of(new PrintStmt(expr)));
    Program result = pass.run(input);
    Expr folded = ((PrintStmt) result.statements().get(0)).value();
    assertInstanceOf(BoolLiteral.class, folded);
    assertTrue(((BoolLiteral) folded).value());
  }

  @Test void foldsNegation() {
    Expr expr = new UnaryExpr(UnaryExpr.Op.NEG, new IntLiteral(7));
    Program input = new Program(List.of(new PrintStmt(expr)));
    Program result = pass.run(input);
    Expr folded = ((PrintStmt) result.statements().get(0)).value();
    assertInstanceOf(IntLiteral.class, folded);
    assertEquals(-7, ((IntLiteral) folded).value());
  }

  @Test void doesNotFoldNonConstantExpressions() {
    Expr expr = new BinExpr(BinExpr.Op.ADD, new Identifier("x"), new IntLiteral(1));
    Program input = new Program(List.of(new PrintStmt(expr)));
    Program result = pass.run(input);
    Expr folded = ((PrintStmt) result.statements().get(0)).value();
    assertInstanceOf(BinExpr.class, folded);
  }

  @Test void foldsAssignmentValue() {
    Assignment assign = new Assignment("x", new BinExpr(BinExpr.Op.MUL, new IntLiteral(3), new IntLiteral(4)));
    Program input = new Program(List.of(assign));
    Program result = pass.run(input);
    Assignment folded = (Assignment) result.statements().get(0);
    assertInstanceOf(IntLiteral.class, folded.value());
    assertEquals(12, ((IntLiteral) folded.value()).value());
  }
}
