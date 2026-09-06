package xyz.spigotrce.gyalang.ast;

public record GetAttr(Expr receiver, String name) implements Expr {}
