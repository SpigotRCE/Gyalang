package xyz.spigotrce.gyalang.ast;

public record UnaryExpr(Op op, Expr operand) implements Expr {
  public enum Op {
    NOT,
    NEG
  }
}
