package xyz.spigotrce.gyalang.ast;

public record IfStmt(Expr condition, Block thenBlock, Block elseBlock) implements Stmt {

  public IfStmt(Expr condition, Block thenBlock) {
    this(condition, thenBlock, null);
  }
}
