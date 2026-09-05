package xyz.spigotrce.gyalang.ast;

public record VarDecl(String name, String type, Expr value) implements Stmt {
}
