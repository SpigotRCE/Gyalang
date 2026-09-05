package xyz.spigotrce.gyalang.optimizer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.Assignment;
import xyz.spigotrce.gyalang.ast.BinExpr;
import xyz.spigotrce.gyalang.ast.Block;
import xyz.spigotrce.gyalang.ast.CallExpr;
import xyz.spigotrce.gyalang.ast.ClassDef;
import xyz.spigotrce.gyalang.ast.Expr;
import xyz.spigotrce.gyalang.ast.ExprStmt;
import xyz.spigotrce.gyalang.ast.FloatLiteral;
import xyz.spigotrce.gyalang.ast.FuncDef;
import xyz.spigotrce.gyalang.ast.GetAttr;
import xyz.spigotrce.gyalang.ast.Identifier;
import xyz.spigotrce.gyalang.ast.IntLiteral;
import xyz.spigotrce.gyalang.ast.Param;
import xyz.spigotrce.gyalang.ast.Program;
import xyz.spigotrce.gyalang.ast.ReturnStmt;
import xyz.spigotrce.gyalang.ast.SetAttr;
import xyz.spigotrce.gyalang.ast.Stmt;
import xyz.spigotrce.gyalang.ast.StringLiteral;
import xyz.spigotrce.gyalang.ast.VarDecl;
import xyz.spigotrce.gyalang.ast.WhileStmt;

class TypeCheckPassTest {

  private final TypeCheckPass pass = new TypeCheckPass();

  @Test void typedDeclarationIsAccepted() {
    assertDoesNotThrow(() -> check(new VarDecl("a", "int", new IntLiteral(1))));
  }

  private void check(Stmt... stmts) {
    pass.run(new Program(List.of(mainClass(stmts))));
  }

  private static ClassDef mainClass(Stmt... stmts) {
    return new ClassDef("Main", List.of(
        new FuncDef("main", List.of(new Param("self", "obj")), new Block(List.of(stmts)))));
  }

  @Test void annotationWithoutValueIsAccepted() {
    assertDoesNotThrow(() -> check(new VarDecl("a", "float", null)));
  }

  @Test void intWidensToFloat() {
    assertDoesNotThrow(() -> check(new VarDecl("a", "float", new IntLiteral(1))));
  }

  @Test void classTypedLocalIsAccepted() {
    assertDoesNotThrow(() -> check(
        new VarDecl("a", "Main", new CallExpr(new Identifier("Main"), List.of()))));
  }

  @Test void mismatchedAssignmentIsRejected() {
    assertThrows(IllegalStateException.class,
        () -> check(
            new VarDecl("a", "int", new IntLiteral(1)),
            new Assignment("a", new StringLiteral("oops"))));
  }

  @Test void redeclarationIsRejected() {
    assertThrows(IllegalStateException.class,
        () -> check(
            new VarDecl("a", "int", new IntLiteral(1)),
            new VarDecl("a", "float", new IntLiteral(2))));
  }

  @Test void untypedBindingFixesType() {
    assertDoesNotThrow(() -> check(
        new Assignment("a", new IntLiteral(1)),
        new Assignment("a", new IntLiteral(2))));
    assertThrows(IllegalStateException.class,
        () -> check(
            new Assignment("a", new IntLiteral(1)),
            new Assignment("a", new FloatLiteral(2.5))));
  }

  @Test void unknownTypeIsRejected() {
    assertThrows(IllegalStateException.class, () -> check(new VarDecl("a", "doggo", null)));
  }

  @Test void readBeforeDeclarationIsRejected() {
    assertThrows(IllegalStateException.class,
        () -> check(print(new Identifier("x"))));
  }

  private static Stmt print(Expr value) {
    return new ExprStmt(new CallExpr(new Identifier("print"), List.of(value)));
  }

  @Test void untypedParametersAreAssignableAsObjects() {
    FuncDef f = new FuncDef("f",
        List.of(new Param("self", "obj"), new Param("p", "obj")),
        new Block(List.of(
            new Assignment("p", new StringLiteral("x")),
            new ReturnStmt(new Identifier("p")))));
    assertDoesNotThrow(() -> checkProgram(
        new ClassDef("Util", List.of(f)),
        mainClass(
            new VarDecl("u", "Util", new CallExpr(new Identifier("Util"), List.of())),
            call(new CallExpr(new GetAttr(new Identifier("u"), "f"),
                List.of(new StringLiteral("x")))))));
  }

  private void checkProgram(ClassDef... classes) {
    pass.run(new Program(List.of(classes)));
  }

  private static ExprStmt call(CallExpr call) {
    return new ExprStmt(call);
  }

  @Test void mixedIntFloatArithmeticIsAccepted() {
    Expr mixed = new BinExpr(BinExpr.Op.ADD, new IntLiteral(1), new FloatLiteral(2.5));
    assertDoesNotThrow(() -> check(new Assignment("x", mixed)));
  }

  @Test void stringConcatenationIsAccepted() {
    Expr concat = new BinExpr(BinExpr.Op.ADD, new StringLiteral("a"), new StringLiteral("b"));
    assertDoesNotThrow(() -> check(new Assignment("x", concat)));
  }

  @Test void stringSubIsRejected() {
    Expr bad = new BinExpr(BinExpr.Op.SUB, new StringLiteral("a"), new StringLiteral("b"));
    assertThrows(IllegalStateException.class, () -> check(new Assignment("x", bad)));
  }

  @Test void loopWithIncrementedCounterIsAccepted() {
    assertDoesNotThrow(() -> check(
        new Assignment("i", new IntLiteral(0)),
        new WhileStmt(
            new BinExpr(BinExpr.Op.LT, new Identifier("i"), new IntLiteral(3)),
            new Block(List.of(
                new Assignment("i",
                    new BinExpr(BinExpr.Op.ADD, new Identifier("i"), new IntLiteral(1))))))));
  }

  @Test void typedFieldDeclarationIsAccepted() {
    assertDoesNotThrow(() -> check(
        new SetAttr(new Identifier("self"), "count", new IntLiteral(0), "int"),
        print(new GetAttr(new Identifier("self"), "count"))));
  }

  @Test void mismatchedFieldAssignmentIsRejected() {
    assertThrows(IllegalStateException.class, () -> check(
        new SetAttr(new Identifier("self"), "count", new IntLiteral(0), "int"),
        new SetAttr(new Identifier("self"), "count", new StringLiteral("x"), null)));
  }

  @Test void fieldAccessOnPrimitiveIsRejected() {
    assertThrows(IllegalStateException.class, () -> check(
        new VarDecl("x", "int", new IntLiteral(1)),
        print(new GetAttr(new Identifier("x"), "anything"))));
  }

  @Test void constructorArgumentCountIsChecked() {
    ClassDef counter = counterClass();
    assertDoesNotThrow(() -> checkProgram(counter,
        mainClass(new VarDecl("c", "Counter", new CallExpr(new Identifier("Counter"),
            List.of(new IntLiteral(5)))))));
    assertThrows(IllegalStateException.class, () -> checkProgram(counter,
        mainClass(call(new CallExpr(new Identifier("Counter"), List.of(new IntLiteral(1),
            new IntLiteral(2)))))));
    assertThrows(IllegalStateException.class, () -> checkProgram(counter,
        mainClass(call(new CallExpr(new Identifier("Counter"),
            List.of(new StringLiteral("x")))))));
  }

  private static ClassDef counterClass() {
    return new ClassDef("Counter", List.of(
        new FuncDef("__init__",
            List.of(new Param("self", "obj"), new Param("start", "int")),
            new Block(List.of(new SetAttr(new Identifier("self"), "count",
                new Identifier("start"), "int")))),
        new FuncDef("add", List.of(new Param("self", "obj"), new Param("n", "int")),
            new Block(List.of(new SetAttr(new Identifier("self"), "count",
                new BinExpr(BinExpr.Op.ADD, new GetAttr(new Identifier("self"), "count"),
                    new Identifier("n")), null)))),
        new FuncDef("get", List.of(new Param("self", "obj")),
            new Block(List.of(new ReturnStmt(new GetAttr(new Identifier("self"), "count")))))));
  }

  @Test void constructorWithoutArgumentsIsAllowed() {
    assertDoesNotThrow(() -> checkProgram(
        new ClassDef("Empty", List.of()),
        mainClass(new VarDecl("e", "Empty", new CallExpr(new Identifier("Empty"), List.of())))));
  }

  @Test void instanceMethodCallIsChecked() {
    ClassDef counter = counterClass();
    assertDoesNotThrow(() -> checkProgram(counter,
        mainClass(
            new VarDecl("c", "Counter", new CallExpr(new Identifier("Counter"), List.of(new IntLiteral(1)))),
            call(new CallExpr(new GetAttr(new Identifier("c"), "add"), List.of(new IntLiteral(2)))),
            print(new CallExpr(new GetAttr(new Identifier("c"), "get"), List.of())))));
    assertThrows(IllegalStateException.class, () -> checkProgram(counter,
        mainClass(
            new VarDecl("c", "Counter", new CallExpr(new Identifier("Counter"), List.of(new IntLiteral(1)))),
            call(new CallExpr(new GetAttr(new Identifier("c"), "add"), List.of(new StringLiteral("x")))))));
    assertThrows(IllegalStateException.class, () -> checkProgram(counter,
        mainClass(
            new VarDecl("c", "Counter", new CallExpr(new Identifier("Counter"), List.of(new IntLiteral(1)))),
            call(new CallExpr(new GetAttr(new Identifier("c"), "missing"), List.of())))));
  }

  @Test void staticMethodCallIsChecked() {
    ClassDef util = new ClassDef("Util", List.of(
        new FuncDef("double", List.of(new Param("n", "int")),
            new Block(List.of(new ReturnStmt(
                new BinExpr(BinExpr.Op.MUL, new Identifier("n"), new IntLiteral(2))))))));
    assertDoesNotThrow(() -> checkProgram(util,
        mainClass(print(new CallExpr(new GetAttr(new Identifier("Util"), "double"),
            List.of(new IntLiteral(21)))))));
    assertThrows(IllegalStateException.class, () -> checkProgram(util,
        mainClass(call(new CallExpr(new GetAttr(new Identifier("Util"), "double"),
            List.of(new StringLiteral("x")))))));
  }

  @Test void instanceMethodCannotBeCalledStatically() {
    ClassDef counter = new ClassDef("Counter", List.of(
        new FuncDef("get", List.of(new Param("self", "obj")),
            new Block(List.of(new ReturnStmt(new IntLiteral(1)))))));
    assertThrows(IllegalStateException.class, () -> checkProgram(counter,
        mainClass(call(new CallExpr(new GetAttr(new Identifier("Counter"), "get"), List.of())))));
  }

  @Test void topLevelNonClassStatementIsRejected() {
    assertThrows(IllegalStateException.class, () -> pass.run(new Program(
        List.of(print(new IntLiteral(1))))));
  }

  @Test void duplicateMethodIsRejected() {
    FuncDef duplicate = new FuncDef("f", List.of(new Param("self", "obj")), new Block(List.of()));
    assertThrows(IllegalStateException.class, () -> checkProgram(
        new ClassDef("Dup", List.of(duplicate, duplicate))));
  }
}
