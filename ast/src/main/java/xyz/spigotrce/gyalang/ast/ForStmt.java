package xyz.spigotrce.gyalang.ast;

public record ForStmt(String variable, Expr iterable, Block body) implements Stmt {}
