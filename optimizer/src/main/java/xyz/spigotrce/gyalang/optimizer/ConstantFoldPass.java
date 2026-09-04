package xyz.spigotrce.gyalang.optimizer;

import xyz.spigotrce.gyalang.ast.*;

import java.util.ArrayList;
import java.util.List;

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
    if (stmt instanceof PrintStmt printStmt) {
      return new PrintStmt(foldExpr(printStmt.value()));
    }
    if (stmt instanceof Assignment assign) {
      return new Assignment(assign.name(), foldExpr(assign.value()));
    }
    if (stmt instanceof Block block) {
      List<Stmt> inner = new ArrayList<>();
      for (Stmt s : block.statements()) {
        inner.add(foldStmt(s));
      }
      return new Block(inner);
    }
    return stmt;
  }

  Expr foldExpr(Expr expr) {
    if (expr instanceof BinExpr bin) {
      Expr left = foldExpr(bin.left());
      Expr right = foldExpr(bin.right());
      if (left instanceof IntLiteral l && right instanceof IntLiteral r) {
        return foldIntBin(bin.op(), l.value(), r.value());
      }
      if (left instanceof FloatLiteral l && right instanceof FloatLiteral r) {
        return foldFloatBin(bin.op(), l.value(), r.value());
      }
      return new BinExpr(bin.op(), left, right);
    }
    if (expr instanceof UnaryExpr unary) {
      Expr operand = foldExpr(unary.operand());
      if (unary.op() == UnaryExpr.Op.NEG && operand instanceof IntLiteral intLit) {
        return new IntLiteral(-intLit.value());
      }
      return new UnaryExpr(unary.op(), operand);
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
