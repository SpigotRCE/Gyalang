package xyz.spigotrce.gyalang;

public record Token(Type type, String value, String filename, int position) {

  @Override
  public String toString() {
    return type + "('" + value + "')";
  }

  public enum Type {
    IDENT,
    INT,
    FLOAT,
    STRING,
    KEYWORD,
    NEWLINE,
    EOF
  }
}
