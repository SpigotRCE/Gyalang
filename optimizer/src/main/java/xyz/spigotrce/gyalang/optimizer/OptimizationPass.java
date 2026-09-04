package xyz.spigotrce.gyalang.optimizer;

import xyz.spigotrce.gyalang.ast.Program;

public interface OptimizationPass {
  String getName();

  Program run(Program program);
}
