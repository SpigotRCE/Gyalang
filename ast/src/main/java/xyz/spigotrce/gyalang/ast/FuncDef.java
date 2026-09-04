package xyz.spigotrce.gyalang.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record FuncDef(String name, List<String> parameters, Block body) implements Stmt {
  public FuncDef(String name, List<String> parameters, Block body) {
    this.name = name;
    this.parameters = new ArrayList<>(parameters);
    this.body = body;
  }

  @Override public List<String> parameters() {
    return Collections.unmodifiableList(parameters);
  }
}
