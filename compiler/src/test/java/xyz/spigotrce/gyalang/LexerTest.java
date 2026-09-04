package xyz.spigotrce.gyalang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {

  @Test void emptySource() {
    Lexer lexer = new Lexer("", "test.glg");
    List<Token> tokens = lexer.tokenize();
    assertEquals(1, tokens.size());
    assertEquals(Token.Type.EOF, tokens.getFirst().type());
  }

  @Test void stringLiteral() {
    Lexer lexer = new Lexer("\"hello\"", "test.glg");
    List<Token> tokens = lexer.tokenize();
    assertEquals(Token.Type.STRING, tokens.getFirst().type());
    assertEquals("hello", tokens.getFirst().value());
  }

  @Test void integerLiteral() {
    Lexer lexer = new Lexer("42", "test.glg");
    List<Token> tokens = lexer.tokenize();
    assertEquals(Token.Type.INT, tokens.getFirst().type());
    assertEquals("42", tokens.getFirst().value());
  }

  @Test void floatLiteral() {
    Lexer lexer = new Lexer("3.14", "test.glg");
    List<Token> tokens = lexer.tokenize();
    assertEquals(Token.Type.FLOAT, tokens.getFirst().type());
    assertEquals("3.14", tokens.getFirst().value());
  }

  @Test void identifier() {
    Lexer lexer = new Lexer("myVar", "test.glg");
    List<Token> tokens = lexer.tokenize();
    assertEquals(Token.Type.IDENT, tokens.getFirst().type());
    assertEquals("myVar", tokens.getFirst().value());
  }

  @Test void keyword() {
    Lexer lexer = new Lexer("if", "test.glg");
    List<Token> tokens = lexer.tokenize();
    assertEquals(Token.Type.KEYWORD, tokens.getFirst().type());
    assertEquals("if", tokens.getFirst().value());
  }

  @Test void commentSkipped() {
    Lexer lexer = new Lexer("# this is a comment\nx", "test.glg");
    List<Token> tokens = lexer.tokenize();
    // comment text is skipped, newline is kept, then x is IDENT
    Token first = tokens.stream().filter(t -> t.type() == Token.Type.IDENT).findFirst().orElseThrow();
    assertEquals("x", first.value());
  }

  @Test void newPositionTracked() {
    Lexer lexer = new Lexer("ab", "test.glg");
    List<Token> tokens = lexer.tokenize();
    assertEquals(0, tokens.getFirst().position());
  }

  @Test void mixedTokens() {
    Lexer lexer = new Lexer("x 1", "test.glg");
    List<Token> tokens = lexer.tokenize();
    assertEquals(Token.Type.IDENT, tokens.getFirst().type());
    assertEquals(Token.Type.INT, tokens.get(1).type());
  }
}
