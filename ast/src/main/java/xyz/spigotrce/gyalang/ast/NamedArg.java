package xyz.spigotrce.gyalang.ast;

/**
 * A named argument in a call: {@code name = value}. In Python these are keyword arguments, e.g.
 * {@code print("a", "b", sep=", ")}.
 */
public record NamedArg(String name, Expr value) {}
