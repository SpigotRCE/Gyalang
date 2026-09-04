package xyz.spigotrce.gyalang;

import java.util.List;
import xyz.spigotrce.gyalang.ast.Program;

public class Parser {
  private final List<Token> tokens;
  private final String filename;
  private int pos;

  public Parser(List<Token> tokens, String filename) {
    this.tokens = tokens;
    this.filename = filename;
  }

  public Program parse() {
    // TODO: implement full parser
    return new Program(List.of());
  }

  private boolean match(Token.Type type) {
    if (check(type)) {
      advance();
      return true;
    }
    return false;
  }

  private boolean check(Token.Type type) {
    return peek().type() == type;
  }

  private Token advance() {
    Token token = tokens.get(pos);
    pos++;
    return token;
  }

  private Token peek() {
    return tokens.get(pos);
  }
}
