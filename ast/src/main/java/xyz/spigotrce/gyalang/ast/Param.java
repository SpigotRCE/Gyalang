package xyz.spigotrce.gyalang.ast;

/**
 * A single declared parameter of a function/method.
 *
 * <p>In Python a function has one signature made of ordinary parameters,
 * optional parameters (those with a default value) and, finally, a varargs
 * parameter ({@code *args}) / keyword parameter ({@code **kwargs}). When we
 * compile that single Python signature to JVM bytecode we emit one method
 * whose parameter list contains every parameter (the packed varargs array
 * included); the default values are supplied at each callsite rather than
 * inside the method body.
 *
 * @param name     the parameter name
 * @param type     the declared type ("obj" if unannotated)
 * @param isVararg true if this parameter collects extra positional args ({@code *args})
 * @param default_ the default value expression, or null if this parameter has none
 */
public record Param(String name, String type, boolean isVararg, Expr default_) {
  public Param(String name, String type) {
    this(name, type, false, null);
  }
}
