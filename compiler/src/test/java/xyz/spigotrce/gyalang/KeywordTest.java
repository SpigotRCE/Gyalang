package xyz.spigotrce.gyalang;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeywordTest {

  @Test
  void recognizedKeywords() {
    assertTrue(Keyword.isKeyword("if"));
    assertTrue(Keyword.isKeyword("while"));
    assertTrue(Keyword.isKeyword("def"));
    assertTrue(Keyword.isKeyword("return"));
    assertTrue(Keyword.isKeyword("True"));
    assertTrue(Keyword.isKeyword("False"));
    assertTrue(Keyword.isKeyword("None"));
    assertTrue(Keyword.isKeyword("print"));
    assertTrue(Keyword.isKeyword("class"));
    assertTrue(Keyword.isKeyword("import"));
  }

  @Test
  void notKeywords() {
    assertFalse(Keyword.isKeyword("x"));
    assertFalse(Keyword.isKeyword("hello"));
    assertFalse(Keyword.isKeyword("123"));
    assertFalse(Keyword.isKeyword(""));
  }
}
