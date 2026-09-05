package xyz.spigotrce.gyalang.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ClassDef(String name, List<FuncDef> methods) implements Stmt {
  public ClassDef(String name, List<FuncDef> methods) {
    this.name = name;
    this.methods = new ArrayList<>(methods);
  }

  @Override public List<FuncDef> methods() {
    return Collections.unmodifiableList(methods);
  }
}
