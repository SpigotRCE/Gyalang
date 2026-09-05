package xyz.spigotrce.gyalang.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A call expression. Positional arguments come first, followed by any named
 * (keyword) arguments, same as Python's call ordering rules.
 *
 * @param callee    the function/object being called
 * @param arguments positional arguments
 * @param keywords  named arguments, e.g. {@code sep=", "}
 */
public record CallExpr(Expr callee, List<Expr> arguments, List<NamedArg> keywords) implements Expr {
  public CallExpr(Expr callee, List<Expr> arguments) {
    this(callee, arguments, List.of());
  }

  public CallExpr(Expr callee, List<Expr> arguments, List<NamedArg> keywords) {
    this.callee = callee;
    this.arguments = new ArrayList<>(arguments);
    this.keywords = keywords == null ? List.of() : new ArrayList<>(keywords);
  }

  @Override public List<Expr> arguments() {
    return Collections.unmodifiableList(arguments);
  }

  @Override public List<NamedArg> keywords() {
    return Collections.unmodifiableList(keywords);
  }
}
