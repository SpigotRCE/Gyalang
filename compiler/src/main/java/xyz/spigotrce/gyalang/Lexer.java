package xyz.spigotrce.gyalang;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
  private final String source;
  private final String filename;
  private int pos;

  public Lexer(String source, String filename) {
    this.source = source;
    this.filename = filename;
  }

  public List<Token> tokenize() {
    List<Token> tokens = new ArrayList<>();
    while (pos < source.length()) {
      char c = source.charAt(pos);
      if (c == ' ' || c == '\t' || c == '\r') {
        pos++;
      } else if (c == '\n') {
        tokens.add(new Token(Token.Type.NEWLINE, "\n", filename, pos));
        pos++;
      } else if (c == '#') {
        skipComment();
      } else if (c == '"') {
        tokens.add(readString());
      } else if (Character.isDigit(c)) {
        tokens.add(readNumber());
      } else if (Character.isLetter(c) || c == '_') {
        tokens.add(readIdentifier());
      } else {
        tokens.add(new Token(Token.Type.EOF, String.valueOf(c), filename, pos));
        pos++;
      }
    }
    tokens.add(new Token(Token.Type.EOF, "", filename, pos));
    return tokens;
  }

  private void skipComment() {
    while (pos < source.length() && source.charAt(pos) != '\n') {
      pos++;
    }
  }

  private Token readString() {
    int start = pos;
    pos++; // skip opening quote
    StringBuilder sb = new StringBuilder();
    while (pos < source.length() && source.charAt(pos) != '"') {
      sb.append(source.charAt(pos));
      pos++;
    }
    pos++; // skip closing quote
    return new Token(Token.Type.STRING, sb.toString(), filename, start);
  }

  private Token readNumber() {
    int start = pos;
    while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
      pos++;
    }
    if (pos < source.length() && source.charAt(pos) == '.') {
      pos++;
      while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
        pos++;
      }
      return new Token(Token.Type.FLOAT, source.substring(start, pos), filename, start);
    }
    return new Token(Token.Type.INT, source.substring(start, pos), filename, start);
  }

  private Token readIdentifier() {
    int start = pos;
    while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
      pos++;
    }
    String word = source.substring(start, pos);
    Token.Type type = Keyword.isKeyword(word) ? Token.Type.KEYWORD : Token.Type.IDENT;
    return new Token(type, word, filename, start);
  }
}
