package xyz.spigotrce.gyalang.ast;

public record BinExpr(Op op, Expr left, Expr right) implements Expr {
  public enum Op {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    EQ,
    NEQ,
    LT,
    GT,
    LTE,
    GTE,
    AND,
    OR
  }
}
