package xyz.spigotrce.gyalang.ast;

public record WhileStmt(Expr condition, Block body) implements Stmt {
}
