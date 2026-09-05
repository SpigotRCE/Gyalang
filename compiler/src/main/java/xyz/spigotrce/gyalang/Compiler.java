package xyz.spigotrce.gyalang;

import java.util.List;
import java.util.Map;
import xyz.spigotrce.gyalang.ast.ClassDef;
import xyz.spigotrce.gyalang.ast.FuncDef;
import xyz.spigotrce.gyalang.ast.Program;
import xyz.spigotrce.gyalang.ast.Stmt;
import xyz.spigotrce.gyalang.optimizer.ConstantFoldPass;
import xyz.spigotrce.gyalang.optimizer.Optimizer;
import xyz.spigotrce.gyalang.optimizer.TypeCheckPass;

public class Compiler {
  private final String source;
  private final String filename;

  private final Optimizer optimizer;

  public Compiler(String source, String filename) {
    this(source, filename, defaultOptimizer());
  }

  public Compiler(String source, String filename, Optimizer optimizer) {
    this.source = source;
    this.filename = filename;
    this.optimizer = optimizer;
  }

  private static Optimizer defaultOptimizer() {
    Optimizer optimizer = new Optimizer();
    optimizer.addPass(new ConstantFoldPass());
    optimizer.addPass(new TypeCheckPass());
    return optimizer;
  }

  public String getSource() {
    return source;
  }

  public String getFilename() {
    return filename;
  }

  public Map<String, byte[]> compile() {
    Program program = parseAndOptimize();
    requireEntryPoint(program);
    CodeGenerator codegen = new CodeGenerator(filename);
    return codegen.generateClasses(program);
  }

  public Program parseAndOptimize() {
    return optimizer.run(parse());
  }

  private void requireEntryPoint(Program program) {
    for (Stmt stmt : program.statements()) {
      if (stmt instanceof ClassDef(String name, List<FuncDef> methods) && name.equals("Main")) {
        for (FuncDef method : methods) {
          boolean instanceMain = method.name().equals("main")
              && !method.parameters().isEmpty()
              && method.parameters().getFirst().name().equals("self");
          if (!instanceMain && method.name().equals("main")) {
            return;
          }
        }
        throw new IllegalStateException("Entry class 'Main' must define a main() method");
      }
    }
    throw new IllegalStateException("Program must define an entry class named 'Main'");
  }

  public Program parse() {
    Lexer lexer = new Lexer(source, filename);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens, filename);
    return parser.parse();
  }
}
