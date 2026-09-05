package xyz.spigotrce.gyalang;

import java.util.ArrayList;
import java.util.List;
import xyz.spigotrce.gyalang.ast.Assignment;
import xyz.spigotrce.gyalang.ast.BinExpr;
import xyz.spigotrce.gyalang.ast.Block;
import xyz.spigotrce.gyalang.ast.BoolLiteral;
import xyz.spigotrce.gyalang.ast.CallExpr;
import xyz.spigotrce.gyalang.ast.ClassDef;
import xyz.spigotrce.gyalang.ast.Expr;
import xyz.spigotrce.gyalang.ast.ExprStmt;
import xyz.spigotrce.gyalang.ast.FloatLiteral;
import xyz.spigotrce.gyalang.ast.ForStmt;
import xyz.spigotrce.gyalang.ast.FuncDef;
import xyz.spigotrce.gyalang.ast.GetAttr;
import xyz.spigotrce.gyalang.ast.Identifier;
import xyz.spigotrce.gyalang.ast.IfStmt;
import xyz.spigotrce.gyalang.ast.IntLiteral;
import xyz.spigotrce.gyalang.ast.NamedArg;
import xyz.spigotrce.gyalang.ast.Param;
import xyz.spigotrce.gyalang.ast.PassStmt;
import xyz.spigotrce.gyalang.ast.Program;
import xyz.spigotrce.gyalang.ast.ReturnStmt;
import xyz.spigotrce.gyalang.ast.SetAttr;
import xyz.spigotrce.gyalang.ast.Stmt;
import xyz.spigotrce.gyalang.ast.StringLiteral;
import xyz.spigotrce.gyalang.ast.UnaryExpr;
import xyz.spigotrce.gyalang.ast.VarDecl;
import xyz.spigotrce.gyalang.ast.WhileStmt;

public class Parser {
  private final List<Token> tokens;
  private final String filename;
  private int pos;

  public Parser(List<Token> tokens, String filename) {
    this.tokens = tokens;
    this.filename = filename;
  }

  public Program parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!check(Token.Type.EOF)) {
      if (match(Token.Type.NEWLINE)) {
        continue;
      }
      if (match(Token.Type.STRING)) {
        expectEol();
        continue;
      }
      statements.add(classDef());
    }
    return new Program(statements);
  }

  private Stmt classDef() {
    expectKeyword("class");
    Token name = expect(Token.Type.IDENT);
    expect(Token.Type.COLON);
    expectEol();
    expect(Token.Type.INDENT);
    List<FuncDef> methods = new ArrayList<>();
    while (!check(Token.Type.DEDENT) && !check(Token.Type.EOF)) {
      if (match(Token.Type.NEWLINE)) {
        continue;
      }
      if (match(Token.Type.STRING)) {
        expectEol();
        continue;
      }
      methods.add(methodDef());
    }
    expect(Token.Type.DEDENT);
    return new ClassDef(name.value(), methods);
  }

  private FuncDef methodDef() {
    expectKeyword("def");
    Token name = expect(Token.Type.IDENT);
    expect(Token.Type.LPAREN);
    List<Param> parameters = new ArrayList<>();
    if (!check(Token.Type.RPAREN)) {
      do {
        parameters.add(parameter());
      } while (match(Token.Type.COMMA));
    }
    validateParameterOrder(parameters, name.value());
    expect(Token.Type.RPAREN);
    expect(Token.Type.COLON);
    Block body = block();
    return new FuncDef(name.value(), parameters, body);
  }

  private void validateParameterOrder(List<Param> params, String funcName) {
    boolean seenDefault = false;
    for (int i = 0; i < params.size(); i++) {
      Param p = params.get(i);
      if (p.isVararg()) {
        for (int j = i + 1; j < params.size(); j++) {
          if (params.get(j).isVararg()) {
            throw error("function '" + funcName + "' cannot have multiple *args parameters");
          }
        }
        break;
      }
      if (p.default_() != null) {
        seenDefault = true;
      } else if (seenDefault) {
        throw error("non-default argument '" + p.name() + "' follows default argument in function '" + funcName + "'");
      }
    }
  }

  private Param parameter() {
    boolean vararg = match(Token.Type.STAR);
    Token name = expect(Token.Type.IDENT);
    String type = "obj";
    if (check(Token.Type.COLON)) {
      advance();
      type = expectTypeAnnotation();
    }
    Expr def = null;
    if (match(Token.Type.EQUALS)) {
      def = expression();
    }
    return new Param(name.value(), type, vararg, def);
  }

  private Stmt statement() {
    Token token = peek();
    if (token.type() == Token.Type.KEYWORD) {
      return switch (token.value()) {
        case "if" -> ifStatement();
        case "for" -> forStatement();
        case "while" -> whileStatement();
        case "return" -> returnStatement();
        case "pass" -> passStatement();
        default -> assignmentOrExpression();
      };
    }
    if (token.type() == Token.Type.STRING) {
      advance();
      expectEol();
      return new PassStmt();
    }
    return assignmentOrExpression();
  }

  private Stmt assignmentOrExpression() {
    Expr head = postfix();
    if (isSimpleIdentifier(head)) {
      if (match(Token.Type.COLON)) {
        String type = expectTypeAnnotation();
        Expr value = match(Token.Type.EQUALS) ? expression() : null;
        expectEol();
        return new VarDecl(((Identifier) head).name(), type, value);
      }
      if (match(Token.Type.EQUALS)) {
        Expr value = expression();
        expectEol();
        return new Assignment(((Identifier) head).name(), value);
      }
      return callOrError(head);
    }
    if (head instanceof GetAttr(Expr receiver, String name)) {
      String type = null;
      if (match(Token.Type.COLON)) {
        type = expectTypeAnnotation();
      }
      if (match(Token.Type.EQUALS)) {
        Expr value = expression();
        expectEol();
        return new SetAttr(receiver, name, value, type);
      }
      if (type != null) {
        expectEol();
        return new SetAttr(receiver, name, null, type);
      }
      return callOrError(head);
    }
    return callOrError(head);
  }

  private Stmt callOrError(Expr head) {
    expectEol();
    if (head instanceof CallExpr) {
      return new ExprStmt(head);
    }
    throw error("Unsupported standalone expression: " + head);
  }

  private boolean isSimpleIdentifier(Expr expr) {
    return expr instanceof Identifier;
  }

  private boolean checkKeywordAfterIdent() {
    return check(Token.Type.IDENT) && peek(1).type() == Token.Type.EQUALS;
  }

  private String expectTypeAnnotation() {
    Token token = peek();
    if (token.type() == Token.Type.KEYWORD && isTypeAnnotation(token.value())) {
      advance();
      return token.value();
    }
    if (token.type() == Token.Type.IDENT) {
      advance();
      return token.value();
    }
    throw error("Expected type annotation but found " + token);
  }

  private void expectEol() {
    if (match(Token.Type.NEWLINE)) {
      return;
    }
    if (check(Token.Type.EOF)) {
      return;
    }
    throw error("Expected end of line but found " + peek());
  }

  private Block block() {
    expectEol();
    expect(Token.Type.INDENT);
    List<Stmt> statements = new ArrayList<>();
    while (!check(Token.Type.DEDENT) && !check(Token.Type.EOF)) {
      if (match(Token.Type.NEWLINE)) {
        continue;
      }
      statements.add(statement());
    }
    expect(Token.Type.DEDENT);
    return new Block(statements);
  }

  private Stmt passStatement() {
    expectKeyword("pass");
    expectEol();
    return new PassStmt();
  }

  private Stmt ifStatement() {
    expectKeyword("if");
    Expr condition = expression();
    expect(Token.Type.COLON);
    Block thenBlock = block();
    return ifTail(condition, thenBlock);
  }

  private Stmt ifTail(Expr condition, Block thenBlock) {
    Block elseBlock = null;
    if (checkKeyword("elif")) {
      advance();
      Expr elifCondition = expression();
      expect(Token.Type.COLON);
      Block elifBlock = block();
      elseBlock = new Block(List.of(ifTail(elifCondition, elifBlock)));
    } else if (checkKeyword("else")) {
      advance();
      expect(Token.Type.COLON);
      elseBlock = block();
    }
    return new IfStmt(condition, thenBlock, elseBlock);
  }

  private Stmt whileStatement() {
    expectKeyword("while");
    Expr condition = expression();
    expect(Token.Type.COLON);
    Block body = block();
    return new WhileStmt(condition, body);
  }

  private Stmt forStatement() {
    expectKeyword("for");
    Token variable = expect(Token.Type.IDENT);
    expectKeyword("in");
    Expr iterable = expression();
    expect(Token.Type.COLON);
    Block body = block();
    return new ForStmt(variable.value(), iterable, body);
  }

  private Stmt returnStatement() {
    expectKeyword("return");
    Expr value = null;
    if (!check(Token.Type.NEWLINE)) {
      value = expression();
    }
    expectEol();
    return new ReturnStmt(value);
  }

  private Expr expression() {
    return or();
  }

  private Expr or() {
    Expr left = and();
    while (checkKeyword("or")) {
      advance();
      left = new BinExpr(BinExpr.Op.OR, left, and());
    }
    return left;
  }

  private Expr and() {
    Expr left = not();
    while (checkKeyword("and")) {
      advance();
      left = new BinExpr(BinExpr.Op.AND, left, not());
    }
    return left;
  }

  private Expr not() {
    if (checkKeyword("not")) {
      advance();
      return new UnaryExpr(UnaryExpr.Op.NOT, not());
    }
    return comparison();
  }

  private Expr comparison() {
    Expr left = additive();
    while (isComparisonOp(peek().type())) {
      BinExpr.Op op = comparisonOp(peek().type());
      advance();
      left = new BinExpr(op, left, additive());
    }
    return left;
  }

  private Expr additive() {
    Expr left = multiplicative();
    while (peek().type() == Token.Type.PLUS || peek().type() == Token.Type.MINUS) {
      BinExpr.Op op = peek().type() == Token.Type.PLUS ? BinExpr.Op.ADD : BinExpr.Op.SUB;
      advance();
      left = new BinExpr(op, left, multiplicative());
    }
    return left;
  }

  private Expr multiplicative() {
    Expr left = unary();
    while (peek().type() == Token.Type.STAR
        || peek().type() == Token.Type.SLASH
        || peek().type() == Token.Type.PERCENT) {
      BinExpr.Op op = switch (peek().type()) {
        case STAR -> BinExpr.Op.MUL;
        case SLASH -> BinExpr.Op.DIV;
        case PERCENT -> BinExpr.Op.MOD;
        default -> throw error("Unexpected token: " + peek());
      };
      advance();
      left = new BinExpr(op, left, unary());
    }
    return left;
  }

  private Expr unary() {
    if (peek().type() == Token.Type.MINUS) {
      advance();
      return new UnaryExpr(UnaryExpr.Op.NEG, unary());
    }
    if (peek().type() == Token.Type.PLUS) {
      advance();
      return unary();
    }
    return postfix();
  }

  /** Parses postfix call chains and attribute access: a.b.c(...) */
  private Expr postfix() {
    Expr base = primary();
    while (true) {
      if (check(Token.Type.DOT)) {
        advance();
        Token name = expect(Token.Type.IDENT);
        base = new GetAttr(base, name.value());
        continue;
      }
      if (check(Token.Type.LPAREN)) {
        advance();
        List<Expr> arguments = new ArrayList<>();
        List<NamedArg> keywords = new ArrayList<>();
        boolean seenKeyword = false;
        if (!check(Token.Type.RPAREN)) {
          do {
            if (checkKeywordAfterIdent()) {
              seenKeyword = true;
              String kwName = expect(Token.Type.IDENT).value();
              expect(Token.Type.EQUALS);
              keywords.add(new NamedArg(kwName, expression()));
            } else {
              if (seenKeyword) {
                throw error("positional argument follows keyword argument");
              }
              arguments.add(expression());
            }
          } while (match(Token.Type.COMMA));
        }
        expect(Token.Type.RPAREN);
        base = new CallExpr(base, arguments, keywords);
        continue;
      }
      break;
    }
    return base;
  }

  private Expr primary() {
    Token token = peek();
    switch (token.type()) {
      case INT:
        advance();
        return new IntLiteral(Integer.parseInt(token.value()));
      case FLOAT:
        advance();
        return new FloatLiteral(Double.parseDouble(token.value()));
      case STRING:
        advance();
        return new StringLiteral(token.value());
      case IDENT:
        advance();
        return new Identifier(token.value());
      case KEYWORD:
        return switch (token.value()) {
          case "True" -> {
            advance();
            yield new BoolLiteral(true);
          }
          case "False" -> {
            advance();
            yield new BoolLiteral(false);
          }
          case "input", "len", "int", "float", "str", "bool", "print" -> {
            advance();
            yield new Identifier(token.value());
          }
          default -> throw error("Unexpected keyword in expression: " + token);
        };
      case LPAREN:
        advance();
        Expr inner = expression();
        expect(Token.Type.RPAREN);
        return inner;
      default:
        throw error("Unexpected token in expression: " + token);
    }
  }

  private boolean isTypeAnnotation(String type) {
    return type.equals("int") || type.equals("float") || type.equals("str") || type.equals("bool");
  }

  private boolean isComparisonOp(Token.Type type) {
    return type == Token.Type.EQUALS_EQUALS || type == Token.Type.NOT_EQUALS
        || type == Token.Type.LESS || type == Token.Type.GREATER
        || type == Token.Type.LESS_EQUALS || type == Token.Type.GREATER_EQUALS;
  }

  private BinExpr.Op comparisonOp(Token.Type type) {
    return switch (type) {
      case EQUALS_EQUALS -> BinExpr.Op.EQ;
      case NOT_EQUALS -> BinExpr.Op.NEQ;
      case LESS -> BinExpr.Op.LT;
      case GREATER -> BinExpr.Op.GT;
      case LESS_EQUALS -> BinExpr.Op.LTE;
      case GREATER_EQUALS -> BinExpr.Op.GTE;
      default -> throw error("Not a comparison operator: " + type);
    };
  }

  private boolean check(Token.Type type) {
    return peek().type() == type;
  }

  private boolean match(Token.Type type) {
    if (check(type)) {
      advance();
      return true;
    }
    return false;
  }

  private Token expect(Token.Type type) {
    if (check(type)) {
      return advance();
    }
    throw error("Expected " + type + " but found " + peek());
  }

  private void expectKeyword(String keyword) {
    if (checkKeyword(keyword)) {
      advance();
    } else {
      throw error("Expected '" + keyword + "' but found " + peek());
    }
  }

  private boolean checkKeyword(String keyword) {
    Token token = peek();
    return token.type() == Token.Type.KEYWORD && token.value().equals(keyword);
  }

  private Token advance() {
    Token token = tokens.get(pos);
    if (pos < tokens.size() - 1) {
      pos++;
    }
    return token;
  }

  private Token peek() {
    return tokens.get(pos);
  }

  private Token peek(int ahead) {
    return tokens.get(Math.min(pos + ahead, tokens.size() - 1));
  }

  private IllegalStateException error(String message) {
    return new IllegalStateException(message + " in " + filename + " at offset " + peek().position());
  }
}
