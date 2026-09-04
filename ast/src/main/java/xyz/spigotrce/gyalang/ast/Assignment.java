package xyz.spigotrce.gyalang.ast;

public record Assignment(String name, Expr value) implements Stmt {
}
