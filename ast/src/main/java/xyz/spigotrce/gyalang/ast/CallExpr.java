package xyz.spigotrce.gyalang.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CallExpr(Expr callee, List<Expr> arguments) implements Expr {
  public CallExpr(Expr callee, List<Expr> arguments) {
    this.callee = callee;
    this.arguments = new ArrayList<>(arguments);
  }

  @Override public List<Expr> arguments() {
    return Collections.unmodifiableList(arguments);
  }
}
