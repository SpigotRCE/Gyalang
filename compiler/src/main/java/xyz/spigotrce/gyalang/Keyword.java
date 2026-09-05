package xyz.spigotrce.gyalang;

import java.util.Set;

public class Keyword {
  public static final Set<String> KEYWORDS = Set.of(
      "if", "elif", "else",
      "while", "for", "in",
      "def", "return",
      "True", "False", "None",
      "and", "or", "not",
      "pass", "break", "continue",
      "print", "input", "len", "int", "float", "str", "bool",
      "class", "import", "from", "as",
      "try", "except", "finally", "raise",
      "global", "nonlocal", "lambda", "yield"
  );

  private Keyword() {
  }

  public static boolean isKeyword(String word) {
    return KEYWORDS.contains(word);
  }
}
