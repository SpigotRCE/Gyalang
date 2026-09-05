package xyz.spigotrce.gyalang.optimizer;

import java.util.ArrayList;
import java.util.List;
import xyz.spigotrce.gyalang.ast.Assignment;
import xyz.spigotrce.gyalang.ast.BinExpr;
import xyz.spigotrce.gyalang.ast.Block;
import xyz.spigotrce.gyalang.ast.BoolLiteral;
import xyz.spigotrce.gyalang.ast.CallExpr;
import xyz.spigotrce.gyalang.ast.Expr;
import xyz.spigotrce.gyalang.ast.ExprStmt;
import xyz.spigotrce.gyalang.ast.FloatLiteral;
import xyz.spigotrce.gyalang.ast.ForStmt;
import xyz.spigotrce.gyalang.ast.Identifier;
import xyz.spigotrce.gyalang.ast.IntLiteral;
import xyz.spigotrce.gyalang.ast.NamedArg;
import xyz.spigotrce.gyalang.ast.Program;
import xyz.spigotrce.gyalang.ast.Stmt;
import xyz.spigotrce.gyalang.ast.UnaryExpr;

public class ConstantFoldPass implements OptimizationPass {

  @Override public String getName() {
    return "ConstantFold";
  }

  @Override public Program run(Program program) {
    List<Stmt> folded = new ArrayList<>();
    for (Stmt stmt : program.statements()) {
      folded.add(foldStmt(stmt));
    }
    return new Program(folded);
  }

  private Stmt foldStmt(Stmt stmt) {
    if (stmt instanceof ExprStmt(Expr expr)
        && expr instanceof CallExpr(Expr callee, List<Expr> arguments, List<NamedArg> keywords)
        && callee instanceof Identifier(String name)) {
      List<Expr> foldedArgs = new ArrayList<>();
      for (Expr arg : arguments) {
        foldedArgs.add(foldExpr(arg));
      }
      List<NamedArg> foldedKw = new ArrayList<>();
      for (NamedArg kw : keywords) {
        foldedKw.add(new NamedArg(kw.name(), foldExpr(kw.value())));
      }
      return new ExprStmt(new CallExpr(callee, foldedArgs, foldedKw));
    }
    if (stmt instanceof Assignment(String name, Expr value)) {
      return new Assignment(name, foldExpr(value));
    }
    if (stmt instanceof Block(List<Stmt> statements)) {
      List<Stmt> inner = new ArrayList<>();
      for (Stmt s : statements) {
        inner.add(foldStmt(s));
      }
      return new Block(inner);
    }
    if (stmt instanceof ForStmt(String variable, Expr iterable, Block body)) {
      return new ForStmt(variable, foldExpr(iterable), (Block) foldStmt(body));
    }
    return stmt;
  }

  public Expr foldExpr(Expr expr) {
    if (expr instanceof BinExpr(BinExpr.Op op, Expr left1, Expr right1)) {
      Expr left = foldExpr(left1);
      Expr right = foldExpr(right1);
      if (left instanceof IntLiteral(int value) && right instanceof IntLiteral(int value2)) {
        return foldIntBin(op, value, value2);
      }
      if (left instanceof FloatLiteral(double value) && right instanceof FloatLiteral(double value1)) {
        return foldFloatBin(op, value, value1);
      }
      return new BinExpr(op, left, right);
    }
    if (expr instanceof UnaryExpr(UnaryExpr.Op op, Expr operand1)) {
      Expr operand = foldExpr(operand1);
      if (op == UnaryExpr.Op.NEG && operand instanceof IntLiteral(int value)) {
        return new IntLiteral(-value);
      }
      return new UnaryExpr(op, operand);
    }
    return expr;
  }

  private Expr foldIntBin(BinExpr.Op op, int left, int right) {
    return switch (op) {
      case ADD -> new IntLiteral(left + right);
      case SUB -> new IntLiteral(left - right);
      case MUL -> new IntLiteral(left * right);
      case DIV -> right != 0 ? new IntLiteral(left / right) : null;
      case MOD -> right != 0 ? new IntLiteral(left % right) : null;
      case EQ -> new BoolLiteral(left == right);
      case NEQ -> new BoolLiteral(left != right);
      case LT -> new BoolLiteral(left < right);
      case GT -> new BoolLiteral(left > right);
      case LTE -> new BoolLiteral(left <= right);
      case GTE -> new BoolLiteral(left >= right);
      default -> null;
    };
  }

  private Expr foldFloatBin(BinExpr.Op op, double left, double right) {
    return switch (op) {
      case ADD -> new FloatLiteral(left + right);
      case SUB -> new FloatLiteral(left - right);
      case MUL -> new FloatLiteral(left * right);
      case DIV -> new FloatLiteral(left / right);
      case EQ -> new BoolLiteral(left == right);
      case NEQ -> new BoolLiteral(left != right);
      case LT -> new BoolLiteral(left < right);
      case GT -> new BoolLiteral(left > right);
      case LTE -> new BoolLiteral(left <= right);
      case GTE -> new BoolLiteral(left >= right);
      default -> null;
    };
  }
}
