package xyz.spigotrce.gyalang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Lexer {
  private final String source;
  private final String filename;
  private final Deque<Integer> indents = new ArrayDeque<>();
  private final List<Token> tokens = new ArrayList<>();
  private int pos;
  private boolean atLineStart = true;
  private int lineIndent;

  public Lexer(String source, String filename) {
    this.source = source;
    this.filename = filename;
    indents.push(0);
  }

  public List<Token> tokenize() {
    if (source.startsWith("\uFEFF")) {
      pos = 1;
    }
    while (pos < source.length()) {
      char c = source.charAt(pos);

      if (atLineStart) {
        if (c == ' ' || c == '\t') {
          lineIndent++;
          pos++;
          continue;
        }
        if (c == '\r') {
          pos++;
          continue;
        }
        if (c == '\n') {
          // blank line: no tokens
          lineIndent = 0;
          pos++;
          continue;
        }
        if (c == '#') {
          skipToLineEnd();
          lineIndent = 0;
          continue;
        }
        // real content starts: emit INDENT/DEDENT for this line's indentation
        emitIndentChanges();
        atLineStart = false;
      }

      if (c == '\n') {
        tokens.add(new Token(Token.Type.NEWLINE, "\n", filename, pos));
        pos++;
        atLineStart = true;
        lineIndent = 0;
        continue;
      }
      if (c == '\r') {
        pos++;
      } else if (c == ' ' || c == '\t') {
        pos++;
      } else if (c == '#') {
        skipToLineEnd();
      } else if (c == '"' || c == '\'') {
        tokens.add(readString());
      } else if (Character.isDigit(c)) {
        tokens.add(readNumber());
      } else if (Character.isLetter(c) || c == '_') {
        tokens.add(readIdentifier());
      } else {
        tokens.add(readOperator());
      }
    }

    if (!atLineStart) {
      tokens.add(new Token(Token.Type.NEWLINE, "\n", filename, pos));
    }

    while (indents.size() > 1) {
      indents.pop();
      tokens.add(new Token(Token.Type.DEDENT, "", filename, pos));
    }
    tokens.add(new Token(Token.Type.EOF, "", filename, pos));
    return tokens;
  }

  private void skipToLineEnd() {
    while (pos < source.length() && source.charAt(pos) != '\n') {
      pos++;
    }
  }

  private void emitIndentChanges() {
    int level = lineIndent;
    int current = indents.peek();
    if (level > current) {
      indents.push(level);
      tokens.add(new Token(Token.Type.INDENT, "", filename, pos));
    } else if (level < current) {
      while (indents.peek() > level) {
        indents.pop();
        tokens.add(new Token(Token.Type.DEDENT, "", filename, pos));
      }
      if (indents.peek() != level) {
        throw new IllegalStateException("Inconsistent indentation in " + filename + " at offset " + pos);
      }
    }
  }

  private Token readString() {
    int start = pos;
    char quote = source.charAt(pos);
    boolean triple = peek(1) == quote && peek(2) == quote;
    if (triple) {
      pos += 3;
    } else {
      pos++; // skip opening quote
    }
    StringBuilder sb = new StringBuilder();
    // triple-quoted strings may span multiple lines
    while (pos < source.length()) {
      if (triple) {
        if (source.charAt(pos) == quote && peek(1) == quote && peek(2) == quote) {
          pos += 3;
          break;
        }
      } else if (source.charAt(pos) == quote) {
        pos++; // skip closing quote
        break;
      }
      sb.append(source.charAt(pos));
      pos++;
    }
    return new Token(Token.Type.STRING, sb.toString(), filename, start);
  }

  private Token readNumber() {
    int start = pos;
    while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
      pos++;
    }
    if (pos < source.length() && source.charAt(pos) == '.') {
      do {
        pos++;
      } while (pos < source.length() && Character.isDigit(source.charAt(pos)));
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

  private Token readOperator() {
    char c = source.charAt(pos);
    char next = peek(1);
    switch (c) {
      case '=' -> {
        if (next == '=') {
          pos += 2;
          return new Token(Token.Type.EQUALS_EQUALS, "==", filename, pos - 2);
        }
        pos++;
        return new Token(Token.Type.EQUALS, "=", filename, pos - 1);
      }
      case '!' -> {
        if (next == '=') {
          pos += 2;
          return new Token(Token.Type.NOT_EQUALS, "!=", filename, pos - 2);
        }
        throw new IllegalStateException("Unexpected character '!' in " + filename + " at offset " + pos);
      }
      case '<' -> {
        if (next == '=') {
          pos += 2;
          return new Token(Token.Type.LESS_EQUALS, "<=", filename, pos - 2);
        }
        pos++;
        return new Token(Token.Type.LESS, "<", filename, pos - 1);
      }
      case '>' -> {
        if (next == '=') {
          pos += 2;
          return new Token(Token.Type.GREATER_EQUALS, ">=", filename, pos - 2);
        }
        pos++;
        return new Token(Token.Type.GREATER, ">", filename, pos - 1);
      }
      case '+' -> {
        pos++;
        return new Token(Token.Type.PLUS, "+", filename, pos - 1);
      }
      case '-' -> {
        pos++;
        return new Token(Token.Type.MINUS, "-", filename, pos - 1);
      }
      case '*' -> {
        pos++;
        return new Token(Token.Type.STAR, "*", filename, pos - 1);
      }
      case '/' -> {
        pos++;
        return new Token(Token.Type.SLASH, "/", filename, pos - 1);
      }
      case '%' -> {
        pos++;
        return new Token(Token.Type.PERCENT, "%", filename, pos - 1);
      }
      case '(' -> {
        pos++;
        return new Token(Token.Type.LPAREN, "(", filename, pos - 1);
      }
      case ')' -> {
        pos++;
        return new Token(Token.Type.RPAREN, ")", filename, pos - 1);
      }
      case ':' -> {
        pos++;
        return new Token(Token.Type.COLON, ":", filename, pos - 1);
      }
      case '.' -> {
        pos++;
        return new Token(Token.Type.DOT, ".", filename, pos - 1);
      }
      case ',' -> {
        pos++;
        return new Token(Token.Type.COMMA, ",", filename, pos - 1);
      }
      default -> throw new IllegalStateException(
          "Unexpected character '" + c + "' in " + filename + " at offset " + pos);
    }
  }

  private char peek(int ahead) {
    int p = pos + ahead;
    return p < source.length() ? source.charAt(p) : '\0';
  }
}
