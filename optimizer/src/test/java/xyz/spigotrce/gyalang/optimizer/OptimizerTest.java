package xyz.spigotrce.gyalang.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.CallExpr;
import xyz.spigotrce.gyalang.ast.ExprStmt;
import xyz.spigotrce.gyalang.ast.Identifier;
import xyz.spigotrce.gyalang.ast.IntLiteral;
import xyz.spigotrce.gyalang.ast.Program;

class OptimizerTest {

  @Test void noPassesReturnsOriginal() {
    Optimizer opt = new Optimizer();
    Program program = new Program(List.of(
        new ExprStmt(new CallExpr(new Identifier("print"), List.of(new IntLiteral(1))))));
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

  private static class NoOpPass implements OptimizationPass {
    @Override public String getName() {
      return "NoOp";
    }

    @Override public Program run(Program program) {
      return new Program(List.of());
    }
  }

  private record RecordingPass(StringBuilder log, String label) implements OptimizationPass {

    @Override public String getName() {
        return "Recording(" + label + ")";
      }

      @Override public Program run(Program program) {
        log.append(label);
        return program;
      }
    }
}
