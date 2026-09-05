package xyz.spigotrce.gyalang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.spigotrce.gyalang.ast.Assignment;
import xyz.spigotrce.gyalang.ast.BinExpr;
import xyz.spigotrce.gyalang.ast.Block;
import xyz.spigotrce.gyalang.ast.BoolLiteral;
import xyz.spigotrce.gyalang.ast.CallExpr;
import xyz.spigotrce.gyalang.ast.ClassDef;
import xyz.spigotrce.gyalang.ast.Expr;
import xyz.spigotrce.gyalang.ast.ExprStmt;
import xyz.spigotrce.gyalang.ast.ForStmt;
import xyz.spigotrce.gyalang.ast.FuncDef;
import xyz.spigotrce.gyalang.ast.GetAttr;
import xyz.spigotrce.gyalang.ast.Identifier;
import xyz.spigotrce.gyalang.ast.IfStmt;
import xyz.spigotrce.gyalang.ast.IntLiteral;
import xyz.spigotrce.gyalang.ast.Param;
import xyz.spigotrce.gyalang.ast.PassStmt;
import xyz.spigotrce.gyalang.ast.Program;
import xyz.spigotrce.gyalang.ast.ReturnStmt;
import xyz.spigotrce.gyalang.ast.SetAttr;
import xyz.spigotrce.gyalang.ast.StringLiteral;
import xyz.spigotrce.gyalang.ast.VarDecl;
import xyz.spigotrce.gyalang.ast.WhileStmt;

class ParserTest {

  @Test void emptySource() {
    assertInstanceOf(Program.class, parse(""));
  }

  private Program parse(String source) {
    Lexer lexer = new Lexer(source, "test.glg");
    Parser parser = new Parser(lexer.tokenize(), "test.glg");
    return parser.parse();
  }

  @Test void parsesPrint() {
    Block body = mainBody(main("print(\"hi\")"));
    ExprStmt exprStmt = assertInstanceOf(ExprStmt.class, body.statements().getFirst());
    CallExpr call = assertInstanceOf(CallExpr.class, exprStmt.expr());
    assertEquals("print", assertInstanceOf(Identifier.class, call.callee()).name());
    StringLiteral value = assertInstanceOf(StringLiteral.class, call.arguments().getFirst());
    assertEquals("hi", value.value());
  }

  private static Block mainBody(String source) {
    return mainOf(parseOr(source)).body();
  }

  private static String main(String... bodyLines) {
    StringBuilder sb = new StringBuilder("""
        class Main:
            def main(self):
        """);
    for (String line : bodyLines) {
      sb.append("        ").append(line).append('\n');
    }
    return sb.toString();
  }

  private static FuncDef mainOf(Program program) {
    ClassDef clazz = assertInstanceOf(ClassDef.class, program.statements().getFirst());
    return clazz.methods().getFirst();
  }

  private static Program parseOr(String source) {
    Lexer lexer = new Lexer(source, "test.glg");
    Parser parser = new Parser(lexer.tokenize(), "test.glg");
    return parser.parse();
  }

  @Test void parsesAssignment() {
    Block body = mainBody(main("x = 42"));
    Assignment assignment = assertInstanceOf(Assignment.class, body.statements().getFirst());
    assertEquals("x", assignment.name());
    assertEquals(42, assertInstanceOf(IntLiteral.class, assignment.value()).value());
  }

  @Test void parsesArithmeticWithPrecedence() {
    Block body = mainBody(main("y = 1 + 2 * 3"));
    Assignment assignment = assertInstanceOf(Assignment.class, body.statements().getFirst());
    BinExpr expr = assertInstanceOf(BinExpr.class, assignment.value());
    assertEquals(BinExpr.Op.ADD, expr.op());
    assertEquals(BinExpr.Op.MUL, assertInstanceOf(BinExpr.class, expr.right()).op());
  }

  @Test void parsesComparisonAndLogical() {
    Block body = mainBody(main("z = 1 < 2 and True"));
    Assignment assignment = assertInstanceOf(Assignment.class, body.statements().getFirst());
    BinExpr and = assertInstanceOf(BinExpr.class, assignment.value());
    assertEquals(BinExpr.Op.AND, and.op());
    assertEquals(BinExpr.Op.LT, assertInstanceOf(BinExpr.class, and.left()).op());
    assertTrue(assertInstanceOf(BoolLiteral.class, and.right()).value());
  }

  @Test void parsesIfElifElse() {
    Block body = mainBody(main(
        "if x > 1:",
        "    print(\"a\")",
        "elif x > 2:",
        "    print(\"b\")",
        "else:",
        "    print(\"c\")"));
    IfStmt outer = assertInstanceOf(IfStmt.class, body.statements().getFirst());
    BinExpr cond = assertInstanceOf(BinExpr.class, outer.condition());
    assertEquals(BinExpr.Op.GT, cond.op());

    Block elseBlock = assertInstanceOf(Block.class, outer.elseBlock());
    IfStmt inner = assertInstanceOf(IfStmt.class, elseBlock.statements().getFirst());
    assertInstanceOf(Block.class, inner.elseBlock());
  }

  @Test void parsesWhileLoop() {
    Block body = mainBody(main(
        "while i < 3:",
        "    print(i)",
        "    i = i + 1"));
    WhileStmt loop = assertInstanceOf(WhileStmt.class, body.statements().getFirst());
    assertInstanceOf(BinExpr.class, loop.condition());
    assertEquals(2, loop.body().statements().size());
  }

  @Test void parsesForLoop() {
    Block body = mainBody(main(
        "for ch in \"abc\":",
        "    print(ch)"));
    ForStmt loop = assertInstanceOf(ForStmt.class, body.statements().getFirst());
    assertEquals("ch", loop.variable());
    assertInstanceOf(StringLiteral.class, loop.iterable());
    assertEquals(1, loop.body().statements().size());
  }

  @Test void parsesRangeForLoop() {
    Block body = mainBody(main(
        "for i in range(1, 5):",
        "    print(i)"));
    ForStmt loop = assertInstanceOf(ForStmt.class, body.statements().getFirst());
    assertEquals("i", loop.variable());
    Expr iterable = loop.iterable();
    assertInstanceOf(CallExpr.class, iterable);
    CallExpr call = (CallExpr) iterable;
    assertInstanceOf(Identifier.class, call.callee());
    assertEquals("range", ((Identifier) call.callee()).name());
  }

  @Test void parsesKeywordArgs() {
    Block body = mainBody(main(
        "print(\"a\", \"b\", sep=\"-\", end=\"|\")"));
    ExprStmt stmt = assertInstanceOf(ExprStmt.class, body.statements().getFirst());
    CallExpr call = assertInstanceOf(CallExpr.class, stmt.expr());
    assertEquals(2, call.arguments().size());
    assertEquals(2, call.keywords().size());
    assertEquals("sep", call.keywords().getFirst().name());
    assertEquals("end", call.keywords().get(1).name());
    assertEquals("|", ((StringLiteral) call.keywords().get(1).value()).value());
  }

  @Test void parsesDefaultParameters() {
    Program program = parseOr("""
        class Main:
            def greet(name, greeting="Hello", punct="!"):
                pass
        """);
    FuncDef greet = mainOf(program);
    assertEquals(3, greet.parameters().size());
    Param greeting = greet.parameters().get(1);
    assertEquals("greeting", greeting.name());
    assertInstanceOf(StringLiteral.class, greeting.default_());
    assertEquals("Hello", ((StringLiteral) greeting.default_()).value());
    assertNull(greet.parameters().getFirst().default_());
  }

  @Test void positionalAfterKeywordIsRejected() {
    assertThrows(IllegalStateException.class, () ->
        parseOr("""
            class Main:
                def main(self):
                    print("a", sep="-", "b")
            """));
  }

  @Test void nonDefaultAfterDefaultParameterIsRejected() {
    assertThrows(IllegalStateException.class, () ->
        parseOr("""
            class Main:
                def f(a=1, b):
                    pass
            """));
  }

  @Test void parsesClassDefinition() {
    Program program = parseOr("""
        class Point:
            def __init__(self, x: int, y: int):
                pass
        """);
    ClassDef clazz = assertInstanceOf(ClassDef.class, program.statements().getFirst());
    assertEquals("Point", clazz.name());
    FuncDef init = clazz.methods().getFirst();
    assertEquals("__init__", init.name());
    assertEquals("self", init.parameters().getFirst().name());
    assertEquals("obj", init.parameters().getFirst().type());
    assertEquals("x", init.parameters().get(1).name());
    assertEquals("int", init.parameters().get(1).type());
    assertEquals("y", init.parameters().get(2).name());
    assertEquals("int", init.parameters().get(2).type());
  }

  @Test void parsesMultipleClasses() {
    Program program = parseOr("""
        class A:
            def main(self):
                pass

        class B:
            def helper(self):
                pass
        """);
    assertEquals(2, program.statements().size());
  }

  @Test void parsesUntypedParameters() {
    Program program = parseOr("""
        class Foo:
            def bar(self, a, b):
                pass
        """);
    FuncDef bar = ((ClassDef) program.statements().getFirst()).methods().getFirst();
    assertEquals(List.of(
        new Param("self", "obj"),
        new Param("a", "obj"),
        new Param("b", "obj")), bar.parameters());
  }

  @Test void parsesStaticMethodWithoutSelf() {
    Program program = parseOr("""
        class Util:
            def double(n: int):
                return n
        """);
    FuncDef method = ((ClassDef) program.statements().getFirst()).methods().getFirst();
    assertEquals(new Param("n", "int"), method.parameters().getFirst());
  }

  @Test void rejectsTopLevelStatement() {
    assertThrows(IllegalStateException.class, () -> parseOr("print(1)\n"));
  }

  @Test void rejectsClassWithoutIndentedBody() {
    assertThrows(IllegalStateException.class, () -> parseOr("""
        class Foo:
        print(1)
        """));
  }

  @Test void parsesMethodDefinition() {
    Program program = parseOr("""
        class Main:
            def add(self, a, b):
                return a
        """);
    FuncDef func = mainOf(program);
    assertEquals("add", func.name());
    assertEquals(List.of(
        new Param("self", "obj"),
        new Param("a", "obj"),
        new Param("b", "obj")), func.parameters());
    ReturnStmt ret = assertInstanceOf(ReturnStmt.class, func.body().statements().getFirst());
    assertInstanceOf(Identifier.class, ret.value());
  }

  @Test void parsesTypedDeclaration() {
    Block body = mainBody(main("a: int = 1"));
    VarDecl decl = assertInstanceOf(VarDecl.class, body.statements().getFirst());
    assertEquals("a", decl.name());
    assertEquals("int", decl.type());
    assertEquals(1, assertInstanceOf(IntLiteral.class, decl.value()).value());
  }

  @Test void parsesTypedDeclarationWithoutValue() {
    Block body = mainBody(main("a: float"));
    VarDecl decl = assertInstanceOf(VarDecl.class, body.statements().getFirst());
    assertEquals("float", decl.type());
    assertNull(decl.value());
  }

  @Test void parsesBuiltinCallExpression() {
    Block body = mainBody(main("x = len(\"hello\")"));
    Assignment assignment = assertInstanceOf(Assignment.class, body.statements().getFirst());
    CallExpr call = assertInstanceOf(CallExpr.class, assignment.value());
    assertEquals("len", assertInstanceOf(Identifier.class, call.callee()).name());
    assertEquals(1, call.arguments().size());
  }

  @Test void parsesPassStatement() {
    Block body = mainBody(main("if x:", "    pass"));
    IfStmt iff = assertInstanceOf(IfStmt.class, body.statements().getFirst());
    assertInstanceOf(PassStmt.class, iff.thenBlock().statements().getFirst());
  }

  @Test void parsesEmptyReturn() {
    Program program = parseOr("""
        class Main:
            def f(self):
                return
        """);
    FuncDef func = mainOf(program);
    ReturnStmt ret = assertInstanceOf(ReturnStmt.class, func.body().statements().getFirst());
    assertNull(ret.value());
  }

  @Test void parsesFileWithoutTrailingNewline() {
    Program program = parseOr("""
        class Main:
            def main(self):
                x = 1
                while x < 2:
                    x = x + 1""");
    ClassDef clazz = assertInstanceOf(ClassDef.class, program.statements().getFirst());
    assertEquals(1, clazz.methods().size());
  }

  @Test void parsesAttributeAssignment() {
    Block body = mainBody(main("self.count: int = 0"));
    SetAttr set = assertInstanceOf(SetAttr.class, body.statements().getFirst());
    assertEquals("self", assertInstanceOf(Identifier.class, set.receiver()).name());
    assertEquals("count", set.name());
    assertEquals("int", set.type());
    assertEquals(0, assertInstanceOf(IntLiteral.class, set.value()).value());
  }

  @Test void parsesAttributeAssignmentWithoutType() {
    Block body = mainBody(main("self.count = 5"));
    SetAttr set = assertInstanceOf(SetAttr.class, body.statements().getFirst());
    assertNull(set.type());
  }

  @Test void parsesMethodCallStatement() {
    Block body = mainBody(main("c.bump()"));
    ExprStmt stmt = assertInstanceOf(ExprStmt.class, body.statements().getFirst());
    CallExpr call = assertInstanceOf(CallExpr.class, stmt.expr());
    GetAttr callee = assertInstanceOf(GetAttr.class, call.callee());
    assertEquals("bump", callee.name());
    assertEquals("c", assertInstanceOf(Identifier.class, callee.receiver()).name());
  }

  @Test void parsesConstructionStatement() {
    Block body = mainBody(main("Counter(4)"));
    ExprStmt stmt = assertInstanceOf(ExprStmt.class, body.statements().getFirst());
    CallExpr call = assertInstanceOf(CallExpr.class, stmt.expr());
    assertEquals("Counter", assertInstanceOf(Identifier.class, call.callee()).name());
    assertEquals(1, call.arguments().size());
  }

  @Test void parsesAttributeRead() {
    Block body = mainBody(main("x = p.count"));
    Assignment assignment = assertInstanceOf(Assignment.class, body.statements().getFirst());
    GetAttr attr = assertInstanceOf(GetAttr.class, assignment.value());
    assertEquals("count", attr.name());
  }

  @Test void parsesStaticCallChain() {
    Block body = mainBody(main("Util.double(21)"));
    ExprStmt stmt = assertInstanceOf(ExprStmt.class, body.statements().getFirst());
    CallExpr call = assertInstanceOf(CallExpr.class, stmt.expr());
    GetAttr callee = assertInstanceOf(GetAttr.class, call.callee());
    assertEquals("Util", assertInstanceOf(Identifier.class, callee.receiver()).name());
    assertEquals("double", callee.name());
  }

  @Test void rejectsStandaloneExpression() {
    assertThrows(IllegalStateException.class, () -> parseOr(main("1 + 2")));
  }

  @Test void rejectsBadIndentation() {
    Lexer lexer = new Lexer("""
        if x:
            print(1)
          print(2)
        """, "test.glg");
    assertThrows(IllegalStateException.class, lexer::tokenize);
  }

  @Test void rejectsUnexpectedCharacters() {
    assertThrows(IllegalStateException.class, () -> new Lexer("x = 1 @ 2", "test.glg").tokenize());
  }
}
