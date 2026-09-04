package xyz.spigotrce.gyalang.ast;

public record ReturnStmt(Expr value) implements Stmt {
  public ReturnStmt() {
    this(null);
  }
}
