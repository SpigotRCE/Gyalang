package xyz.spigotrce.gyalang.ast;

public record SetAttr(Expr receiver, String name, Expr value, String type) implements Stmt {}
