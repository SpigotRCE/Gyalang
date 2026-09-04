package xyz.spigotrce.gyalang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import xyz.spigotrce.gyalang.ast.Program;
import xyz.spigotrce.gyalang.optimizer.ConstantFoldPass;
import xyz.spigotrce.gyalang.optimizer.Optimizer;

public class Main {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Usage: gylang <source.glg>");
      return;
    }

    Path sourcePath = Path.of(args[0]);
    if (!Files.exists(sourcePath)) {
      System.err.println("Error: file not found: " + sourcePath);
      return;
    }

    String source;
    try {
      source = Files.readString(sourcePath);
    } catch (IOException e) {
      System.err.println("Error reading file: " + e.getMessage());
      return;
    }

    Compiler compiler = new Compiler(source, sourcePath.getFileName().toString());
    Program program = compiler.parse();

    Optimizer optimizer = new Optimizer();
    optimizer.addPass(new ConstantFoldPass());
    program = optimizer.run(program);

    System.out.println("Compiled: " + program);
  }
}
