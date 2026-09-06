package xyz.spigotrce.gyalang;

public record Token(Type type, String value, String filename, int position) {
  public enum Type {
    IDENT,
    INT,
    FLOAT,
    STRING,
    KEYWORD,

    NEWLINE,
    INDENT,
    DEDENT,

    EQUALS,
    EQUALS_EQUALS,
    NOT_EQUALS,
    LESS,
    LESS_EQUALS,
    GREATER,
    GREATER_EQUALS,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    LPAREN,
    RPAREN,
    COLON,
    COMMA,
    DOT,

    EOF
  }
}
