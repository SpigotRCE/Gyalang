package xyz.spigotrce.gyalang.optimizer;

import xyz.spigotrce.gyalang.ast.Program;

import java.util.ArrayList;
import java.util.List;

public class Optimizer {
  private final List<OptimizationPass> passes = new ArrayList<>();

  public void addPass(OptimizationPass pass) {
    passes.add(pass);
  }

  public Program run(Program program) {
    Program current = program;
    for (OptimizationPass pass : passes) {
      current = pass.run(current);
    }
    return current;
  }

  public List<OptimizationPass> getPasses() {
    return List.copyOf(passes);
  }
}
