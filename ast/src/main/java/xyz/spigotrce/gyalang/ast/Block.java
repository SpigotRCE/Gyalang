package xyz.spigotrce.gyalang.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Block(List<Stmt> statements) implements Stmt {
  public Block(List<Stmt> statements) {
    this.statements = new ArrayList<>(statements);
  }

  @Override
  public List<Stmt> statements() {
    return Collections.unmodifiableList(statements);
  }
}
