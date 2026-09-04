package xyz.spigotrce.gyalang.optimizer;

import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptimizerTest {

  @Test void noPassesReturnsOriginal() {
    Optimizer opt = new Optimizer();
    Program program = new Program(List.of(new PrintStmt(new IntLiteral(1))));
    Program result = opt.run(program);
    assertSame(program, result);
  }

  @Test void singlePassIsApplied() {
    Optimizer opt = new Optimizer();
    opt.addPass(new NoOpPass());
    Program program = new Program(List.of());
    Program result = opt.run(program);
    assertNotSame(program, result);
  }

  @Test void passesRunInOrder() {
    Optimizer opt = new Optimizer();
    StringBuilder order = new StringBuilder();
    opt.addPass(new RecordingPass(order, "A"));
    opt.addPass(new RecordingPass(order, "B"));
    opt.run(new Program(List.of()));
    assertEquals("AB", order.toString());
  }

  @Test void getPassesReturnsCopy() {
    Optimizer opt = new Optimizer();
    NoOpPass pass = new NoOpPass();
    opt.addPass(pass);
    List<OptimizationPass> passes = opt.getPasses();
    assertEquals(1, passes.size());
    assertThrows(UnsupportedOperationException.class, () -> passes.add(new NoOpPass()));
  }

  static class NoOpPass implements OptimizationPass {
    @Override public String getName() {
      return "NoOp";
    }

    @Override public Program run(Program program) {
      return new Program(List.of());
    }
  }

  static class RecordingPass implements OptimizationPass {
    private final StringBuilder log;
    private final String label;

    RecordingPass(StringBuilder log, String label) {
      this.log = log;
      this.label = label;
    }

    @Override public String getName() {
      return "Recording(" + label + ")";
    }

    @Override public Program run(Program program) {
      log.append(label);
      return program;
    }
  }
}
