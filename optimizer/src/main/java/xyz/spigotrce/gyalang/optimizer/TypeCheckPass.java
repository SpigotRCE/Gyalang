package xyz.spigotrce.gyalang.optimizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

public class TypeCheckPass implements OptimizationPass {

  private static final Set<String> PRIMITIVES = Set.of("int", "float", "str", "bool");
  private static final Set<String> BUILTINS =
      Set.of("print", "input", "len", "int", "float", "str", "bool", "abs", "min", "max", "round", "sqrt", "range", "sum", "ord", "chr");
  private final Map<String, ClassInfo> classes = new HashMap<>();
  private final Deque<Frame> frames = new ArrayDeque<>();

  @Override public String getName() {
    return "TypeCheck";
  }

  @Override public Program run(Program program) {
    classes.clear();
    frames.clear();
    for (Stmt stmt : program.statements()) {
      if (stmt instanceof ClassDef(String name, List<FuncDef> methods)) {
        registerClass(name, methods);
      } else {
        throw illegal("only 'class' definitions are allowed at top level");
      }
    }
    for (Stmt stmt : program.statements()) {
      if (stmt instanceof ClassDef clazz) {
        checkClass(clazz);
      }
    }
    return program;
  }

  private void registerClass(String name, List<FuncDef> methods) {
    ClassInfo info = new ClassInfo();
    for (FuncDef method : methods) {
      if (info.methods.putIfAbsent(method.name(), method) != null) {
        throw illegal("duplicate method '" + method.name() + "' in class " + name);
      }
    }
    classes.put(name, info);
  }

  private void checkClass(ClassDef clazz) {
    ClassInfo info = classes.get(clazz.name());
    predeclareFields(clazz, info);
    for (FuncDef method : clazz.methods()) {
      checkMethod(clazz.name(), info, method);
    }
  }

  private void predeclareFields(ClassDef clazz, ClassInfo info) {
    for (FuncDef method : clazz.methods()) {
      collectFieldDecls(info, method.body());
    }
  }

  private void collectFieldDecls(ClassInfo info, Block block) {
    for (Stmt stmt : block.statements()) {
      switch (stmt) {
        case SetAttr(Identifier self, String name, Expr value, String type) -> {
          if (self.name().equals("self")) {
            if (type != null) {
              info.fields.putIfAbsent(name, resolveType(type));
            } else if (value != null && literalTypeOf(value) != null) {
              info.fields.putIfAbsent(name, literalTypeOf(value));
            }
          }
        }
        case Block inner -> collectFieldDecls(info, inner);
        case IfStmt(Expr c, Block thenBlock, Block elseBlock) -> {
          collectFieldDecls(info, thenBlock);
          if (elseBlock != null) {
            collectFieldDecls(info, elseBlock);
          }
        }
        case WhileStmt(Expr c, Block body) -> collectFieldDecls(info, body);
        case ForStmt(String variable, Expr iterable, Block body) -> collectFieldDecls(info, body);
        default -> {
        }
      }
    }
  }

  private String literalTypeOf(Expr expr) {
    if (expr instanceof IntLiteral) {
      return "int";
    }
    if (expr instanceof FloatLiteral) {
      return "float";
    }
    if (expr instanceof StringLiteral) {
      return "str";
    }
    if (expr instanceof BoolLiteral) {
      return "bool";
    }
    return null;
  }

  private void checkMethod(String className, ClassInfo info, FuncDef method) {
    List<Param> params = method.parameters();
    boolean instance = !params.isEmpty() && params.getFirst().name().equals("self");
    if (!instance && params.stream().anyMatch(p -> p.name().equals("self"))) {
      throw illegal("'self' must be the first parameter of method '" + method.name() + "'");
    }
    Frame frame = new Frame(className, instance);
    if (instance) {
      frame.params.put("self", className);
    }
    for (int i = instance ? 1 : 0; i < params.size(); i++) {
      Param param = params.get(i);
      frame.params.put(param.name(), param.type().equals("obj") ? "obj" : resolveType(param.type()));
    }
    frames.push(frame);
    try {
      checkBlock(method.body());
    } finally {
      frames.pop();
    }
  }

  private void checkBlock(Block block) {
    for (Stmt stmt : block.statements()) {
      checkStmt(stmt);
    }
  }

  private void checkStmt(Stmt stmt) {
    switch (stmt) {
      case ExprStmt(Expr expr) -> checkExpr(expr);
      case Assignment(String name, Expr value) -> checkAssignment(name, value);
      case VarDecl(String name, String type, Expr value) -> checkLocalDecl(name, type, value);
      case SetAttr(Expr receiver, String name, Expr value, String type) -> checkSetAttr(receiver, name, value, type);
      case IfStmt(Expr condition, Block thenBlock, Block elseBlock) -> {
        checkExpr(condition);
        checkBlock(thenBlock);
        if (elseBlock != null) {
          checkBlock(elseBlock);
        }
      }
      case WhileStmt(Expr condition, Block body) -> {
        checkExpr(condition);
        checkBlock(body);
      }
      case ForStmt(String variable, Expr iterable, Block body) -> {
        checkExpr(iterable);
        String iterableType = typeOfExpr(iterable);
        boolean isRange = isRangeCall(iterable);
        if (!isRange && !"str".equals(iterableType) && !"obj".equals(iterableType)) {
          throw illegal("for loop cannot iterate over value of type " + iterableType);
        }
        frame().locals.put(variable, isRange ? "int" : "obj".equals(iterableType) ? "obj" : "str");
        checkBlock(body);
      }
      case ReturnStmt(Expr value) -> {
        if (value != null) {
          checkExpr(value);
        }
      }
      case Block block -> checkBlock(block);
      case PassStmt passStmt -> {
        // nothing to check
      }
      case null, default -> {
        // unknown statements are left to the code generator
      }
    }
  }

  private void checkAssignment(String name, Expr value) {
    checkExpr(value);
    String actual = typeOfExpr(value);
    if (frame().params.containsKey(name)) {
      String declared = frame().params.get(name);
      if (!isObj(declared)) {
        requireAssignable(name, declared, actual);
      }
      return;
    }
    String existing = frame().locals.get(name);
    if (existing == null) {
      frame().locals.put(name, actual);
      return;
    }
    requireAssignable(name, existing, actual);
  }

  private void checkLocalDecl(String name, String type, Expr value) {
    if (frame().params.containsKey(name) || frame().locals.containsKey(name)) {
      throw illegal("variable '" + name + "' is already declared");
    }
    String resolved = resolveType(type);
    if (value != null) {
      checkExpr(value);
      requireAssignable(name, resolved, typeOfExpr(value));
    }
    frame().locals.put(name, resolved);
  }

  private void checkSetAttr(Expr receiver, String name, Expr value, String type) {
    String classOfReceiver = typeOfExpr(receiver);
    if (!classes.containsKey(classOfReceiver)) {
      throw illegal("cannot access attribute '" + name + "' on non-object type " + classOfReceiver);
    }
    ClassInfo info = classes.get(classOfReceiver);
    String declared = type != null ? resolveType(type) : info.fields.get(name);
    if (value != null) {
      checkExpr(value);
      String actual = typeOfExpr(value);
      if (declared != null) {
        requireAssignable(name, declared, actual);
      } else {
        info.fields.putIfAbsent(name, actual);
      }
    } else if (type != null) {
      info.fields.putIfAbsent(name, declared);
    }
  }

  private void checkExpr(Expr expr) {
    typeOfExpr(expr);
  }

  private String typeOfExpr(Expr expr) {
    if (expr instanceof IntLiteral) {
      return "int";
    }
    if (expr instanceof FloatLiteral) {
      return "float";
    }
    if (expr instanceof StringLiteral) {
      return "str";
    }
    if (expr instanceof BoolLiteral) {
      return "bool";
    }
    if (expr instanceof Identifier(String name)) {
      return typeOfIdentifier(name);
    }
    if (expr instanceof GetAttr(Expr receiver, String name)) {
      String classOfReceiver = typeOfExpr(receiver);
      if (!classes.containsKey(classOfReceiver)) {
        throw illegal("cannot access attribute '" + name + "' on non-object type " + classOfReceiver);
      }
      String fieldType = classes.get(classOfReceiver).fields.get(name);
      return fieldType != null ? fieldType : "obj";
    }
    if (expr instanceof UnaryExpr(UnaryExpr.Op op, Expr operand)) {
      return typeOfUnary(op, operand);
    }
    if (expr instanceof BinExpr(BinExpr.Op op, Expr left, Expr right)) {
      return typeOfBinOp(op, left, right);
    }
    if (expr instanceof CallExpr call) {
      return typeOfCall(call);
    }
    return "obj";
  }

  private String typeOfIdentifier(String name) {
    Frame frame = frame();
    if (frame.locals.containsKey(name)) {
      return frame.locals.get(name);
    }
    if (frame.params.containsKey(name)) {
      return frame.params.get(name);
    }
    if (classes.containsKey(name)) {
      return "class:" + name;
    }
    throw illegal("undeclared variable '" + name + "'");
  }

  private String typeOfUnary(UnaryExpr.Op op, Expr operand) {
    if (op == UnaryExpr.Op.NOT) {
      String type = typeOfExpr(operand);
      if (!isJumpable(type)) {
        throw illegal("'not' requires a boolean or int operand, got " + type);
      }
      return "bool";
    }
    String type = typeOfExpr(operand);
    if (!isNumeric(type)) {
      throw illegal("'-' requires a numeric operand, got " + type);
    }
    return type;
  }

  private String typeOfBinOp(BinExpr.Op op, Expr left, Expr right) {
    switch (op) {
      case AND, OR -> {
        String lt = typeOfExpr(left);
        String rt = typeOfExpr(right);
        if (!isJumpable(lt) || !isJumpable(rt)) {
          throw illegal("'" + op.name().toLowerCase() + "' requires boolean or int operands");
        }
        return "bool";
      }
      case EQ, NEQ -> {
        String lt = typeOfExpr(left);
        String rt = typeOfExpr(right);
        boolean numeric = isNumeric(lt) && isNumeric(rt);
        boolean booleans = "bool".equals(lt) && "bool".equals(rt);
        if (!numeric && !booleans) {
          throw illegal("invalid equality comparison between " + lt + " and " + rt);
        }
        return "bool";
      }
      case LT, GT, LTE, GTE -> {
        String lt = typeOfExpr(left);
        String rt = typeOfExpr(right);
        if (!isNumeric(lt) || !isNumeric(rt)) {
          throw illegal("ordering comparison requires numeric operands, got " + lt + " and " + rt);
        }
        return "bool";
      }
      default -> {
        String lt = typeOfExpr(left);
        String rt = typeOfExpr(right);
        if (op == BinExpr.Op.ADD && ("str".equals(lt) || "str".equals(rt))) {
          return "str";
        }
        if (!isNumeric(lt) || !isNumeric(rt)) {
          throw illegal("arithmetic requires numeric operands, got " + lt + " and " + rt);
        }
        return "float".equals(lt) || "float".equals(rt) ? "float" : "int";
      }
    }
  }

  private String typeOfCall(CallExpr call) {
    if (call.callee() instanceof Identifier(String name)) {
      List<String> argTypes = argTypes(call);
      if (isBuiltin(name)) {
        validateKeywords(call, name, builtinKeywordNames(name));
        if ("abs".equals(name) || "min".equals(name) || "max".equals(name)) {
          return checkNumericBuiltin(name, argTypes);
        }
        if ("sum".equals(name)) {
          return "int";
        }
        return builtinType(name);
      }
      if (classes.containsKey(name)) {
        return constructorType(name, call);
      }
      throw illegal("unknown callable '" + name + "'");
    }
    if (call.callee() instanceof GetAttr(Expr receiver, String methodName)) {
      String receiverType = typeOfExpr(receiver);
      if (receiverType.startsWith("class:")) {
        return staticCallType(receiverType.substring("class:".length()), methodName, call);
      }
      return instanceCallType(receiverType, methodName, call);
    }
    throw illegal("unsupported callee: " + call.callee());
  }

  private List<String> argTypes(CallExpr call) {
    List<String> types = new ArrayList<>();
    for (Expr arg : call.arguments()) {
      types.add(typeOfExpr(arg));
    }
    return types;
  }

  private String constructorType(String className, CallExpr call) {
    ClassInfo info = classes.get(className);
    FuncDef constructor = info.methods.get("__init__");
    if (constructor == null && !call.arguments().isEmpty()) {
      throw illegal("class " + className + " has no constructor taking arguments");
    }
    if (constructor != null) {
      checkArgs(constructor, className + ".<init>", call);
    }
    return className;
  }

  String instanceCallType(String receiverType, String methodName, CallExpr call) {
    if (!classes.containsKey(receiverType)) {
      throw illegal("cannot call method '" + methodName + "' on non-object type " + receiverType);
    }
    FuncDef method = classes.get(receiverType).methods.get(methodName);
    if (method == null) {
      throw illegal("no method '" + methodName + "' on class " + receiverType);
    }
    if (isStaticMethod(method)) {
      throw illegal("method '" + methodName + "' is static and cannot be called on an instance");
    }
    checkArgs(method, receiverType + "." + methodName, call);
    return "obj";
  }

  private String staticCallType(String className, String methodName, CallExpr call) {
    FuncDef method = classes.get(className).methods.get(methodName);
    if (method == null) {
      throw illegal("no method '" + methodName + "' on class " + className);
    }
    if (!isStaticMethod(method)) {
      throw illegal("method '" + methodName + "' is an instance method and needs a receiver");
    }
    checkArgs(method, className + "." + methodName, call);
    return "obj";
  }

  private void checkArgs(FuncDef method, String signature, CallExpr call) {
    List<Param> params = method.parameters();
    int start = isStaticMethod(method) ? 0 : 1;
    int varargIndex = -1;
    for (int i = start; i < params.size(); i++) {
      if (params.get(i).isVararg()) {
        varargIndex = i;
        break;
      }
    }
    int namedCount = varargIndex < 0 ? params.size() - start : varargIndex - start;
    List<Expr> positional = call.arguments();
    if (varargIndex < 0 && positional.size() > namedCount) {
      throw illegal("call to '" + signature + "' has too many arguments");
    }
    for (NamedArg kw : call.keywords()) {
      boolean found = false;
      for (int i = start; i < params.size(); i++) {
        if (!params.get(i).isVararg() && params.get(i).name().equals(kw.name())) {
          found = true;
          break;
        }
      }
      if (!found) {
        throw illegal("call to '" + signature + "' has no parameter named '" + kw.name() + "'");
      }
    }
    for (int i = start; i < params.size(); i++) {
      Param param = params.get(i);
      if (param.isVararg()) {
        continue;
      }
      int positionalIndex = i - start;
      Expr value;
      if (positionalIndex < positional.size()) {
        value = positional.get(positionalIndex);
      } else {
        Expr kw = keywordValue(call, param.name());
        if (kw != null) {
          value = kw;
        } else if (param.default_() != null) {
          value = param.default_();
        } else {
          throw illegal("call to '" + signature + "' missing required argument '" + param.name() + "'");
        }
      }
      String paramType = param.type() == null || param.type().equals("obj") ? "obj" : resolveType(param.type());
      if (!isObj(paramType)) {
        requireAssignable(signature, paramType, typeOfExpr(value));
      }
    }
  }

  private Expr keywordValue(CallExpr call, String name) {
    for (NamedArg kw : call.keywords()) {
      if (kw.name().equals(name)) {
        return kw.value();
      }
    }
    return null;
  }

  private void validateKeywords(CallExpr call, String signature, List<String> allowed) {
    for (NamedArg kw : call.keywords()) {
      if (!allowed.contains(kw.name())) {
        throw illegal("call to '" + signature + "' has no keyword argument '" + kw.name() + "'");
      }
      typeOfExpr(kw.value());
    }
  }

  private List<String> builtinKeywordNames(String name) {
    return switch (name) {
      case "print" -> List.of("sep", "end", "file", "flush");
      default -> List.of();
    };
  }

  private boolean isStaticMethod(FuncDef method) {
    return method.parameters().isEmpty() || !method.parameters().getFirst().name().equals("self");
  }

  private boolean isBuiltin(String name) {
    return BUILTINS.contains(name);
  }

  private String builtinType(String name) {
    return switch (name) {
      case "int", "len", "round", "ord" -> "int";
      case "float", "sqrt" -> "float";
      case "str", "input", "chr" -> "str";
      case "bool" -> "bool";
      case "print", "range" -> "obj";
      default -> throw new AssertionError(name);
    };
  }

  private String checkNumericBuiltin(String name, List<String> argTypes) {
    int expected = "abs".equals(name) ? 1 : 2;
    if (argTypes.size() != expected) {
      throw illegal(name + " expects " + expected + " argument(s)");
    }
    for (String t : argTypes) {
      if (!isNumeric(t)) {
        throw illegal(name + " requires numeric operands, got " + t);
      }
    }
    return argTypes.stream().anyMatch(t -> t.equals("float")) ? "float" : "int";
  }

  private boolean isRangeCall(Expr expr) {
    return expr instanceof CallExpr(Expr callee, List<Expr> arguments, List<NamedArg> keywords) && callee instanceof Identifier(String name) &&
        name.equals("range");
  }

  private String resolveType(String type) {
    if (PRIMITIVES.contains(type)) {
      return type;
    }
    if (classes.containsKey(type)) {
      return type;
    }
    throw illegal("unknown type '" + type + "'");
  }

  private void requireAssignable(String name, String declared, String actual) {
    if (isObj(declared)) {
      return;
    }
    if (declared.equals(actual)) {
      return;
    }
    if (declared.equals("float") && actual.equals("int")) {
      return;
    }
    throw illegal("type mismatch for '" + name + "': declared " + declared + " but assigned " + actual);
  }

  private boolean isObj(String type) {
    return "obj".equals(type);
  }

  private boolean isNumeric(String type) {
    return "int".equals(type) || "float".equals(type);
  }

  private boolean isJumpable(String type) {
    return "int".equals(type) || "bool".equals(type);
  }

  private Frame frame() {
    return frames.peek();
  }

  private IllegalStateException illegal(String message) {
    return new IllegalStateException("Type check failed: " + message);
  }

  private static class ClassInfo {
    final Map<String, String> fields = new HashMap<>();
    final Map<String, FuncDef> methods = new HashMap<>();
  }

  private static class Frame {
    final String className;
    final boolean instance;
    final Map<String, String> params = new HashMap<>();
    final Map<String, String> locals = new HashMap<>();

    Frame(String className, boolean instance) {
      this.className = className;
      this.instance = instance;
    }
  }
}
