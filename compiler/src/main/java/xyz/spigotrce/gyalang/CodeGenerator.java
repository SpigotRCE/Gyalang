package xyz.spigotrce.gyalang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
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

/**
 * Generates JVM bytecode (using OW2 ASM) directly from a class-based Gylang
 * {@link Program} AST. Each Gylang class becomes one JVM class; instance
 * methods, {@code __init__} constructors, and the static {@code Main.main}
 * entry point are mapped onto standard JVM constructs. Stack frames are
 * computed by ASM's {@code COMPUTE_FRAMES} which can be replaced with
 * a class hierarchy based computer.
 */
public class CodeGenerator {
  private static final String CLASS_PREFIX = "gyalang/generated/";
  private static final String OBJECT = "Ljava/lang/Object;";

  private static final String STRING_TYPE = "Ljava/lang/String;";

  private final String filename;
  private final Set<String> classNames = new HashSet<>();

  public CodeGenerator(String filename) {
    this.filename = filename;
  }

  public String getFilename() {
    return filename;
  }

  public String getClassName() {
    return "gyalang/generated/Main";
  }

  /**
   * Generates all class files for a program.
   *
   * @return map of internal class name to class file bytes
   */
  public Map<String, byte[]> generateClasses(Program program) {
    ProgramScope scope = new ProgramScope();
    List<ClassDef> classList = new ArrayList<>();
    for (Stmt stmt : program.statements()) {
      if (stmt instanceof ClassDef def) {
        scope.classes.put(def.name(), def);
        classList.add(def);
        classNames.add(def.name());
      }
    }
    for (ClassDef clazz : classList) {
      scope.fields.put(clazz.name(), fieldTypesFor(clazz));
    }

    Map<String, byte[]> result = new HashMap<>();
    result.put(RuntimeBuiltins.CLASS_INTERNAL, RuntimeBuiltins.generate());
    for (ClassDef clazz : classList) {
      result.put(internalName(clazz.name()), generateClass(scope, clazz));
    }
    return result;
  }

  private Map<String, String> fieldTypesFor(ClassDef clazz) {
    Map<String, String> declared = new HashMap<>();
    Map<String, String> literals = new HashMap<>();
    Set<String> names = new HashSet<>();
    for (FuncDef method : clazz.methods()) {
      scanFields(method.body(), declared, literals, names);
    }
    Map<String, String> result = new HashMap<>();
    for (String name : names) {
      result.put(name, declared.getOrDefault(name, literals.getOrDefault(name, OBJECT)));
    }
    return result;
  }

  private void scanFields(Block block, Map<String, String> declared, Map<String, String> literals, Set<String> names) {
    for (Stmt stmt : block.statements()) {
      if (stmt instanceof SetAttr(Identifier receiver, String name, Expr value, String type)) {
        if (receiver.name().equals("self")) {
          names.add(name);
          if (type != null) {
            declared.putIfAbsent(name, descriptorFor(type));
          } else if (value != null && literalDesc(value) != null) {
            literals.putIfAbsent(name, literalDesc(value));
          }
        }
      } else if (stmt instanceof Block inner) {
        scanFields(inner, declared, literals, names);
      } else if (stmt instanceof IfStmt(Expr cond, Block thenBlock, Block elseBlock)) {
        scanFields(thenBlock, declared, literals, names);
        if (elseBlock != null) {
          scanFields(elseBlock, declared, literals, names);
        }
      } else if (stmt instanceof WhileStmt(Expr cond, Block body)) {
        scanFields(body, declared, literals, names);
      } else if (stmt instanceof ForStmt(String variable, Expr iterable, Block body)) {
        scanFields(body, declared, literals, names);
      }
    }
  }

  private byte[] generateClass(ProgramScope scope, ClassDef clazz) {
    Map<String, String> fields = scope.fields.get(clazz.name());
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName(clazz.name()), null, "java/lang/Object", null);

    for (Map.Entry<String, String> field : fields.entrySet()) {
      cw.visitField(Opcodes.ACC_PUBLIC, field.getKey(), field.getValue(), null, null).visitEnd();
    }

    FuncDef constructor = findMethod(clazz, "__init__");
    if (constructor == null) {
      emitDefaultConstructor(cw);
    } else {
      emitFunction(cw, scope, clazz, constructor, true);
    }
    for (FuncDef method : clazz.methods()) {
      if (!method.name().equals("__init__")) {
        emitFunction(cw, scope, clazz, method, false);
      }
    }

    cw.visitEnd();
    return cw.toByteArray();
  }

  private void emitDefaultConstructor(ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private void emitFunction(ClassWriter cw, ProgramScope scope, ClassDef clazz, FuncDef func, boolean isConstructor) {
    String className = clazz.name();
    String internal = internalName(className);
    boolean isInstance = isInstanceMethod(func);
    boolean isMain = className.equals("Main") && !isInstance && func.name().equals("main");
    Map<String, String> fields = scope.fields.get(className);

    String descriptor;
    if (isConstructor) {
      descriptor = "(" + paramDescs(func, true) + ")V";
    } else if (isMain) {
      descriptor = "([Ljava/lang/String;)V";
    } else if (isInstance) {
      descriptor = "(" + paramDescs(func, true) + ")Ljava/lang/Object;";
    } else {
      descriptor = "(" + paramDescs(func, false) + ")Ljava/lang/Object;";
    }

    int access = Opcodes.ACC_PUBLIC | (isMain || !isInstance ? Opcodes.ACC_STATIC : 0);
    MethodVisitor mv = cw.visitMethod(access, isConstructor ? "<init>" : func.name(), descriptor, null, null);
    mv.visitCode();

    MethodFrame frame = new MethodFrame();
    if (isConstructor || isInstance) {
      frame.locals.put("self", new LocalVar(0, "L" + internal + ";"));
      frame.nextSlot = 1;
      if (isConstructor) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      }
    }
    int paramStart = (isConstructor || isInstance) ? 1 : 0;
    for (int i = paramStart; i < func.parameters().size(); i++) {
      Param param = func.parameters().get(i);
      String desc = paramDesc(param);
      frame.locals.put(param.name(), new LocalVar(frame.nextSlot, desc));
      frame.nextSlot += width(desc);
    }
    if (isMain) {
      frame.nextSlot++; // the JVM reserves slot 0 for the implicit String[] args
    }

    MethodCtx ctx = new MethodCtx(className, isConstructor, isMain);
    for (Stmt stmt : func.body().statements()) {
      emitStmt(mv, ctx, frame, scope, fields, stmt);
    }

    if (isConstructor || isMain) {
      mv.visitInsn(Opcodes.RETURN);
    } else {
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private void emitStmt(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Stmt stmt) {
    switch (stmt) {
      case Assignment assignment -> emitAssignment(mv, ctx, frame, scope, fields, assignment);
      case VarDecl varDecl -> emitVarDecl(mv, ctx, frame, scope, fields, varDecl);
      case SetAttr setAttr -> emitSetAttr(mv, ctx, frame, scope, fields, setAttr);
      case ExprStmt exprStmt -> emitExprStmt(mv, ctx, frame, scope, fields, exprStmt);
      case IfStmt ifStmt -> emitIf(mv, ctx, frame, scope, fields, ifStmt);
      case WhileStmt whileStmt -> emitWhile(mv, ctx, frame, scope, fields, whileStmt);
      case ForStmt forStmt -> emitFor(mv, ctx, frame, scope, fields, forStmt);
      case ReturnStmt returnStmt -> emitReturn(mv, ctx, frame, scope, fields, returnStmt);
      case Block(List<Stmt> statements) -> {
        for (Stmt s : statements) {
          emitStmt(mv, ctx, frame, scope, fields, s);
        }
      }
      case PassStmt passStmt -> {
        // no runtime effect
      }
      case null, default -> throw new UnsupportedOperationException("Unsupported statement: " + stmt);
    }
  }

  private void emitAssignment(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Assignment assignment) {
    LocalVar slot = frame.locals.get(assignment.name());
    if (slot == null) {
      slot = allocateLocal(frame, assignment.name(), typeOf(mv, ctx, frame, scope, fields, assignment.value()));
    }
    emitExpr(mv, ctx, frame, scope, fields, assignment.value());
    convert(mv, ctx, frame, scope, fields, assignment.value(), slot.desc);
    store(mv, slot);
  }

  private void emitVarDecl(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, VarDecl varDecl) {
    if (frame.locals.containsKey(varDecl.name())) {
      throw new UnsupportedOperationException("Local already declared: " + varDecl.name());
    }
    String desc = descriptorFor(varDecl.type());
    LocalVar slot = allocateLocal(frame, varDecl.name(), desc);
    if (varDecl.value() != null) {
      emitExpr(mv, ctx, frame, scope, fields, varDecl.value());
      convert(mv, ctx, frame, scope, fields, varDecl.value(), desc);
      store(mv, slot);
    } else {
      // store the type's default so the local's type is defined (JVM verifier requires it)
      switch (desc) {
        case "D" -> mv.visitInsn(Opcodes.DCONST_0);
        case "I", "Z" -> mv.visitInsn(Opcodes.ICONST_0);
        default -> mv.visitInsn(Opcodes.ACONST_NULL);
      }
      store(mv, slot);
    }
  }

  private void emitSetAttr(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, SetAttr setAttr) {
    if (setAttr.value() == null) {
      return; // declaration without initializer: JVM field defaults apply
    }
    String receiverType = typeOf(mv, ctx, frame, scope, fields, setAttr.receiver());
    String internal = internalName(classOf(receiverType));
    emitExpr(mv, ctx, frame, scope, fields, setAttr.receiver());
    emitExpr(mv, ctx, frame, scope, fields, setAttr.value());
    convert(mv, ctx, frame, scope, fields, setAttr.value(), fields.getOrDefault(setAttr.name(), OBJECT));
    mv.visitFieldInsn(Opcodes.PUTFIELD, internal, setAttr.name(), fields.getOrDefault(setAttr.name(), OBJECT));
  }

  private void emitExprStmt(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, ExprStmt exprStmt) {
    String desc = typeOf(mv, ctx, frame, scope, fields, exprStmt.expr());
    emitExpr(mv, ctx, frame, scope, fields, exprStmt.expr());
    mv.visitInsn(width(desc) == 2 ? Opcodes.POP2 : Opcodes.POP);
  }

  private void emitIf(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, IfStmt ifStmt) {
    Label elseLabel = new Label();
    Label end = new Label();

    emitCondition(mv, ctx, frame, scope, fields, ifStmt.condition(), elseLabel);
    emitBlock(mv, ctx, frame, scope, fields, ifStmt.thenBlock());
    if (ifStmt.elseBlock() != null) {
      mv.visitJumpInsn(Opcodes.GOTO, end);
      mv.visitLabel(elseLabel);
      emitBlock(mv, ctx, frame, scope, fields, ifStmt.elseBlock());
    } else {
      mv.visitLabel(elseLabel);
    }
    mv.visitLabel(end);
  }

  private void emitWhile(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, WhileStmt whileStmt) {
    Label loop = new Label();
    Label end = new Label();

    mv.visitLabel(loop);
    emitCondition(mv, ctx, frame, scope, fields, whileStmt.condition(), end);
    emitBlock(mv, ctx, frame, scope, fields, whileStmt.body());
    mv.visitJumpInsn(Opcodes.GOTO, loop);
    mv.visitLabel(end);
  }

  private void emitFor(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, ForStmt forStmt) {
    if (isRangeCall(forStmt.iterable())) {
      emitForRange(mv, ctx, frame, scope, fields, forStmt);
      return;
    }
    String iterableType = typeOf(mv, ctx, frame, scope, fields, forStmt.iterable());
    if ("[Ljava/lang/Object;".equals(iterableType)) {
      emitForObjectArray(mv, ctx, frame, scope, fields, forStmt);
      return;
    }
    LocalVar seq = allocateLocal(frame, "__for_seq_" + frame.nextSlot, "Ljava/lang/String;");
    LocalVar index = allocateLocal(frame, "__for_index_" + frame.nextSlot, "I");
    LocalVar item = allocateLocal(frame, forStmt.variable(), "Ljava/lang/String;");

    mv.visitInsn(Opcodes.ICONST_0);
    store(mv, index);
    emitExpr(mv, ctx, frame, scope, fields, forStmt.iterable());
    store(mv, seq);

    Label loop = new Label();
    Label end = new Label();
    mv.visitLabel(loop);
    load(mv, index);
    load(mv, seq);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    mv.visitJumpInsn(Opcodes.IF_ICMPGE, end);
    load(mv, seq);
    load(mv, index);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(C)Ljava/lang/String;", false);
    store(mv, item);
    emitBlock(mv, ctx, frame, scope, fields, forStmt.body());
    load(mv, index);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IADD);
    store(mv, index);
    mv.visitJumpInsn(Opcodes.GOTO, loop);
    mv.visitLabel(end);
  }

  private void emitForObjectArray(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, ForStmt forStmt) {
    LocalVar arr = allocateLocal(frame, "__for_objarr_" + frame.nextSlot, "[Ljava/lang/Object;");
    LocalVar index = allocateLocal(frame, "__for_index_" + frame.nextSlot, "I");
    LocalVar item = allocateLocal(frame, forStmt.variable(), OBJECT);

    emitExpr(mv, ctx, frame, scope, fields, forStmt.iterable());
    store(mv, arr);
    mv.visitInsn(Opcodes.ICONST_0);
    store(mv, index);

    Label loop = new Label();
    Label end = new Label();
    mv.visitLabel(loop);
    load(mv, index);
    load(mv, arr);
    mv.visitInsn(Opcodes.ARRAYLENGTH);
    mv.visitJumpInsn(Opcodes.IF_ICMPGE, end);
    load(mv, arr);
    load(mv, index);
    mv.visitInsn(Opcodes.AALOAD);
    store(mv, item);
    emitBlock(mv, ctx, frame, scope, fields, forStmt.body());
    load(mv, index);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IADD);
    store(mv, index);
    mv.visitJumpInsn(Opcodes.GOTO, loop);
    mv.visitLabel(end);
  }

  private void emitForRange(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, ForStmt forStmt) {
    LocalVar arr = allocateLocal(frame, "__for_range_" + frame.nextSlot, "[I");
    LocalVar index = allocateLocal(frame, "__for_index_" + frame.nextSlot, "I");
    LocalVar item = allocateLocal(frame, forStmt.variable(), "I");

    emitRangeCall(mv, ctx, frame, scope, fields, (CallExpr) forStmt.iterable());
    store(mv, arr);
    mv.visitInsn(Opcodes.ICONST_0);
    store(mv, index);

    Label loop = new Label();
    Label end = new Label();
    mv.visitLabel(loop);
    load(mv, index);
    load(mv, arr);
    mv.visitInsn(Opcodes.ARRAYLENGTH);
    mv.visitJumpInsn(Opcodes.IF_ICMPGE, end);
    load(mv, arr);
    load(mv, index);
    mv.visitInsn(Opcodes.IALOAD);
    store(mv, item);
    emitBlock(mv, ctx, frame, scope, fields, forStmt.body());
    load(mv, index);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IADD);
    store(mv, index);
    mv.visitJumpInsn(Opcodes.GOTO, loop);
    mv.visitLabel(end);
  }

  private boolean isRangeCall(Expr expr) {
    return expr instanceof CallExpr(Expr callee, List<Expr> arguments, List<NamedArg> keywords) && callee instanceof Identifier(String name) &&
        name.equals("range");
  }

  private void emitRangeCall(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, CallExpr call) {
    requireArityIn(call, "range", 1, 3);
    int n = call.arguments().size();
    List<Expr> args = call.arguments();
    if (n == 1) {
      mv.visitInsn(Opcodes.ICONST_0);
      emitExpr(mv, ctx, frame, scope, fields, args.getFirst());
      convert(mv, ctx, frame, scope, fields, args.getFirst(), "I");
      mv.visitInsn(Opcodes.ICONST_1);
    } else if (n == 2) {
      emitExpr(mv, ctx, frame, scope, fields, args.get(0));
      convert(mv, ctx, frame, scope, fields, args.get(0), "I");
      emitExpr(mv, ctx, frame, scope, fields, args.get(1));
      convert(mv, ctx, frame, scope, fields, args.get(1), "I");
      mv.visitInsn(Opcodes.ICONST_1);
    } else {
      emitExpr(mv, ctx, frame, scope, fields, args.get(0));
      convert(mv, ctx, frame, scope, fields, args.get(0), "I");
      emitExpr(mv, ctx, frame, scope, fields, args.get(1));
      convert(mv, ctx, frame, scope, fields, args.get(1), "I");
      emitExpr(mv, ctx, frame, scope, fields, args.get(2));
      convert(mv, ctx, frame, scope, fields, args.get(2), "I");
    }
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "range", "(III)[I", false);
  }

  private int requireArityIn(CallExpr call, String name, int min, int max) {
    int n = call.arguments().size();
    if (n < min || n > max) {
      throw new UnsupportedOperationException(name + " expects between " + min + " and " + max + " argument(s)");
    }
    return n;
  }

  private void emitBlock(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Block block) {
    for (Stmt s : block.statements()) {
      emitStmt(mv, ctx, frame, scope, fields, s);
    }
  }

  private void emitReturn(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, ReturnStmt returnStmt) {
    if (ctx.isConstructor() || ctx.isVoidMain()) {
      if (returnStmt.value() != null) {
        String desc = typeOf(mv, ctx, frame, scope, fields, returnStmt.value());
        emitExpr(mv, ctx, frame, scope, fields, returnStmt.value());
        mv.visitInsn(width(desc) == 2 ? Opcodes.POP2 : Opcodes.POP);
      }
      mv.visitInsn(Opcodes.RETURN);
      return;
    }
    if (returnStmt.value() != null) {
      emitExpr(mv, ctx, frame, scope, fields, returnStmt.value());
      boxIfNeeded(mv, ctx, frame, scope, fields, returnStmt.value());
      mv.visitInsn(Opcodes.ARETURN);
    } else {
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);
    }
  }

  private void emitCondition(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Expr condition, Label falseLabel) {
    String type = typeOf(mv, ctx, frame, scope, fields, condition);
    if (isPrimitiveNumeric(type) || type.equals("Z")) {
      emitExpr(mv, ctx, frame, scope, fields, condition);
      mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
    } else {
      emitExpr(mv, ctx, frame, scope, fields, condition);
      mv.visitJumpInsn(Opcodes.IFNULL, falseLabel);
      mv.visitInsn(Opcodes.ICONST_1);
    }
  }

  private void emitExpr(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Expr expr) {
    switch (expr) {
      case IntLiteral(int value1) -> emitIntConstant(mv, value1);
      case FloatLiteral(double value) -> emitFloatConstant(mv, value);
      case StringLiteral(String value) -> mv.visitLdcInsn(value);
      case BoolLiteral(boolean value) -> mv.visitInsn(value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
      case Identifier id -> emitIdentifier(mv, frame, id);
      case GetAttr getAttr -> emitGetAttr(mv, ctx, frame, scope, fields, getAttr);
      case BinExpr bin -> emitBinExpr(mv, ctx, frame, scope, fields, bin);
      case UnaryExpr unary -> emitUnaryExpr(mv, ctx, frame, scope, fields, unary);
      case CallExpr call -> emitCall(mv, ctx, frame, scope, fields, call);
      case null, default -> throw new UnsupportedOperationException("Unsupported expression: " + expr);
    }
  }

  private void emitIdentifier(MethodVisitor mv, MethodFrame frame, Identifier id) {
    LocalVar slot = frame.locals.get(id.name());
    if (slot == null) {
      throw new UnsupportedOperationException("Undeclared identifier: " + id.name());
    }
    load(mv, slot);
  }

  private void emitGetAttr(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, GetAttr getAttr) {
    String receiverType = typeOf(mv, ctx, frame, scope, fields, getAttr.receiver());
    emitExpr(mv, ctx, frame, scope, fields, getAttr.receiver());
    mv.visitFieldInsn(Opcodes.GETFIELD, internalName(classOf(receiverType)), getAttr.name(), fields.getOrDefault(getAttr.name(), OBJECT));
  }

  private void emitIntConstant(MethodVisitor mv, int value) {
    if (value >= -1 && value <= 5) {
      mv.visitInsn(Opcodes.ICONST_0 + value);
    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      mv.visitIntInsn(Opcodes.BIPUSH, value);
    } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
      mv.visitIntInsn(Opcodes.SIPUSH, value);
    } else {
      mv.visitLdcInsn(value);
    }
  }

  private void emitFloatConstant(MethodVisitor mv, double value) {
    if (value == 0.0) {
      mv.visitInsn(Opcodes.DCONST_0);
    } else if (value == 1.0) {
      mv.visitInsn(Opcodes.DCONST_1);
    } else {
      mv.visitLdcInsn(value);
    }
  }

  private void emitBinExpr(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, BinExpr bin) {
    if (bin.op() == BinExpr.Op.AND || bin.op() == BinExpr.Op.OR) {
      emitLogical(mv, ctx, frame, scope, fields, bin);
    } else if (isComparison(bin.op())) {
      emitComparison(mv, ctx, frame, scope, fields, bin);
    } else {
      emitArithmetic(mv, ctx, frame, scope, fields, bin);
    }
  }

  private boolean isComparison(BinExpr.Op op) {
    return op == BinExpr.Op.EQ || op == BinExpr.Op.NEQ || op == BinExpr.Op.LT || op == BinExpr.Op.GT || op == BinExpr.Op.LTE || op == BinExpr.Op.GTE;
  }

  private void emitArithmetic(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, BinExpr bin) {
    String leftType = typeOf(mv, ctx, frame, scope, fields, bin.left());
    String rightType = typeOf(mv, ctx, frame, scope, fields, bin.right());

    if (bin.op() == BinExpr.Op.ADD && (isStringType(leftType) || isStringType(rightType))) {
      mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
      mv.visitInsn(Opcodes.DUP);
      mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      emitExpr(mv, ctx, frame, scope, fields, bin.left());
      boxIfNeeded(mv, ctx, frame, scope, fields, bin.left());
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
      emitExpr(mv, ctx, frame, scope, fields, bin.right());
      boxIfNeeded(mv, ctx, frame, scope, fields, bin.right());
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      return;
    }

    boolean floatOp = "D".equals(leftType) || "D".equals(rightType);

    emitNumericOperand(mv, ctx, frame, scope, fields, bin.left(), floatOp);
    emitNumericOperand(mv, ctx, frame, scope, fields, bin.right(), floatOp);

    if (floatOp) {
      mv.visitInsn(switch (bin.op()) {
        case ADD -> Opcodes.DADD;
        case SUB -> Opcodes.DSUB;
        case MUL -> Opcodes.DMUL;
        case DIV -> Opcodes.DDIV;
        case MOD -> Opcodes.DREM;
        default -> throw new UnsupportedOperationException(bin.op().name());
      });
    } else {
      mv.visitInsn(switch (bin.op()) {
        case ADD -> Opcodes.IADD;
        case SUB -> Opcodes.ISUB;
        case MUL -> Opcodes.IMUL;
        case DIV -> Opcodes.IDIV;
        case MOD -> Opcodes.IREM;
        default -> throw new UnsupportedOperationException(bin.op().name());
      });
    }
  }

  private void emitNumericOperand(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Expr operand, boolean floatOp) {
    String t = typeOf(mv, ctx, frame, scope, fields, operand);
    emitExpr(mv, ctx, frame, scope, fields, operand);
    if (isObjectType(t)) {
      // obj-typed locals hold boxed numbers; unbox before arithmetic
      mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", floatOp ? "doubleValue" : "intValue", floatOp ? "()D" : "()I", false);
    } else if (floatOp && "I".equals(t)) {
      mv.visitInsn(Opcodes.I2D);
    }
  }

  private void emitComparison(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, BinExpr bin) {
    String leftType = typeOf(mv, ctx, frame, scope, fields, bin.left());
    String rightType = typeOf(mv, ctx, frame, scope, fields, bin.right());
    boolean floatOp = "D".equals(leftType) || "D".equals(rightType);
    boolean equality = bin.op() == BinExpr.Op.EQ || bin.op() == BinExpr.Op.NEQ;
    boolean objectEq = equality && (isObjectType(leftType) || isObjectType(rightType));

    Label trueLabel = new Label();
    Label end = new Label();

    if (equality && objectEq) {
      emitExpr(mv, ctx, frame, scope, fields, bin.left());
      emitExpr(mv, ctx, frame, scope, fields, bin.right());
      mv.visitJumpInsn(bin.op() == BinExpr.Op.EQ ? Opcodes.IF_ACMPEQ : Opcodes.IF_ACMPNE, trueLabel);
    } else {
      emitNumericOperand(mv, ctx, frame, scope, fields, bin.left(), floatOp);
      emitNumericOperand(mv, ctx, frame, scope, fields, bin.right(), floatOp);
      if (equality && floatOp) {
        mv.visitInsn(Opcodes.DCMPL);
        mv.visitJumpInsn(bin.op() == BinExpr.Op.EQ ? Opcodes.IFEQ : Opcodes.IFNE, trueLabel);
      } else if (equality) {
        mv.visitJumpInsn(bin.op() == BinExpr.Op.EQ ? Opcodes.IF_ICMPEQ : Opcodes.IF_ICMPNE, trueLabel);
      } else if (floatOp) {
        mv.visitInsn(Opcodes.DCMPL);
        mv.visitJumpInsn(switch (bin.op()) {
          case LT -> Opcodes.IFLT;
          case GT -> Opcodes.IFGT;
          case LTE -> Opcodes.IFLE;
          case GTE -> Opcodes.IFGE;
          default -> throw new UnsupportedOperationException(bin.op().name());
        }, trueLabel);
      } else {
        mv.visitJumpInsn(switch (bin.op()) {
          case LT -> Opcodes.IF_ICMPLT;
          case GT -> Opcodes.IF_ICMPGT;
          case LTE -> Opcodes.IF_ICMPLE;
          case GTE -> Opcodes.IF_ICMPGE;
          default -> throw new UnsupportedOperationException(bin.op().name());
        }, trueLabel);
      }
    }

    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitJumpInsn(Opcodes.GOTO, end);
    mv.visitLabel(trueLabel);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitLabel(end);
  }

  private void emitLogical(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, BinExpr bin) {
    boolean isAnd = bin.op() == BinExpr.Op.AND;
    Label shortcut = new Label();
    Label end = new Label();

    emitExpr(mv, ctx, frame, scope, fields, bin.left());
    mv.visitJumpInsn(isAnd ? Opcodes.IFEQ : Opcodes.IFNE, shortcut);
    emitExpr(mv, ctx, frame, scope, fields, bin.right());
    mv.visitJumpInsn(Opcodes.GOTO, end);

    mv.visitLabel(shortcut);
    mv.visitInsn(isAnd ? Opcodes.ICONST_0 : Opcodes.ICONST_1);
    mv.visitLabel(end);
  }

  private void emitUnaryExpr(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, UnaryExpr unary) {
    if (unary.op() == UnaryExpr.Op.NOT) {
      Label trueLabel = new Label();
      Label end = new Label();
      emitExpr(mv, ctx, frame, scope, fields, unary.operand());
      mv.visitJumpInsn(Opcodes.IFEQ, trueLabel);
      mv.visitInsn(Opcodes.ICONST_0);
      mv.visitJumpInsn(Opcodes.GOTO, end);
      mv.visitLabel(trueLabel);
      mv.visitInsn(Opcodes.ICONST_1);
      mv.visitLabel(end);
    } else if (unary.op() == UnaryExpr.Op.NEG) {
      String type = typeOf(mv, ctx, frame, scope, fields, unary.operand());
      emitExpr(mv, ctx, frame, scope, fields, unary.operand());
      mv.visitInsn("D".equals(type) ? Opcodes.DNEG : Opcodes.INEG);
    } else {
      throw new UnsupportedOperationException(unary.op().name());
    }
  }

  private void emitCall(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, CallExpr call) {
    if (call.callee() instanceof Identifier(String callee)) {
      if (classNames.contains(callee)) {
        emitConstruction(mv, ctx, frame, scope, fields, callee, call);
        return;
      }
      emitBuiltin(mv, ctx, frame, scope, fields, callee, call);
      return;
    }
    if (call.callee() instanceof GetAttr(Expr receiver, String methodName)) {
      String receiverType = typeOf(mv, ctx, frame, scope, fields, receiver);
      if (receiverType.startsWith("class:")) {
        emitStaticCall(mv, ctx, frame, scope, fields, receiverType.substring("class:".length()), methodName, call);
      } else {
        emitInstanceCall(mv, ctx, frame, scope, fields, receiver, methodName, call);
      }
      return;
    }
    throw new UnsupportedOperationException("Unsupported callee: " + call.callee());
  }

  private void emitConstruction(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, String callee, CallExpr call) {
    FuncDef constructor = findMethod(scope.classes.get(callee), "__init__");
    String ctorDescriptors = constructor == null ? "" : paramDescs(constructor, true);
    mv.visitTypeInsn(Opcodes.NEW, internalName(callee));
    mv.visitInsn(Opcodes.DUP);
    emitArgs(mv, ctx, frame, scope, fields, call, constructor, true);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName(callee), "<init>", "(" + ctorDescriptors + ")V", false);
  }

  private void emitStaticCall(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, String className, String methodName, CallExpr call) {
    FuncDef method = findMethod(scope.classes.get(className), methodName);
    boolean isMain = className.equals("Main") && methodName.equals("main") && method != null && !isInstanceMethod(method);
    if (isMain) {
      // entry point is not a callable from Gylang code; treat as unsupported
      throw new UnsupportedOperationException("Cannot call Main.main()");
    }
    emitArgs(mv, ctx, frame, scope, fields, call, method, false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName(className), methodName, "(" + paramDescs(method, false) + ")Ljava/lang/Object;", false);
  }

  private void emitInstanceCall(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Expr receiver, String methodName, CallExpr call) {
    String receiverType = typeOf(mv, ctx, frame, scope, fields, receiver);
    String className = classOf(receiverType);
    FuncDef method = findMethod(scope.classes.get(className), methodName);
    if (method == null) {
      throw new UnsupportedOperationException("Unknown method: " + className + "." + methodName);
    }
    if (isInstanceMethod(method)) {
      emitExpr(mv, ctx, frame, scope, fields, receiver);
      emitArgs(mv, ctx, frame, scope, fields, call, method, true);
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internalName(className), methodName, "(" + paramDescs(method, true) + ")Ljava/lang/Object;", false);
    } else {
      // calling a static method through an instance
      emitArgs(mv, ctx, frame, scope, fields, call, method, false);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName(className), methodName, "(" + paramDescs(method, false) + ")Ljava/lang/Object;", false);
    }
  }

  private void emitBuiltin(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, String callee, CallExpr call) {
    switch (callee) {
      case "print" -> {
        // print(*objects, sep=" ", end="\n", file=None, flush=False) -> one method
        // print(Object[], String, String, Object, boolean); callsite forwards every value,
        // defaulting file to null and flush to false.
        emitVarargsCall(mv, ctx, frame, scope, fields, call, "print",
            "([Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Z)Ljava/lang/Object;",
            new String[] {"sep", "end", "file", "flush"},
            new String[] {"Ljava/lang/String;", "Ljava/lang/String;", OBJECT, "Z"},
            new Expr[] {new StringLiteral(" "), new StringLiteral("\n"), null, new BoolLiteral(false)});
      }
      case "input" -> {
        if (call.arguments().isEmpty()) {
          mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
          requireArity(call, "input", 1);
          emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
          boxIfNeeded(mv, ctx, frame, scope, fields, call.arguments().getFirst());
          mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "input", "(Ljava/lang/String;)Ljava/lang/String;", false);
      }
      case "len" -> {
        requireArity(call, "len", 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        boxIfNeeded(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "len", "(Ljava/lang/String;)I", false);
      }
      case "int" -> {
        requireArity(call, "int", 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        boxIfNeeded(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "int", "(Ljava/lang/Object;)I", false);
      }
      case "float" -> {
        requireArity(call, "float", 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        boxIfNeeded(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "float", "(Ljava/lang/Object;)D", false);
      }
      case "str" -> {
        requireArity(call, "str", 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        boxIfNeeded(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "str", "(Ljava/lang/Object;)Ljava/lang/String;", false);
      }
      case "bool" -> {
        requireArity(call, "bool", 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        boxIfNeeded(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "bool", "(Ljava/lang/Object;)Z", false);
      }
      case "abs" -> {
        requireArity(call, "abs", 1);
        boolean f = isFloatArg(mv, ctx, frame, scope, fields, call);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        if (f) {
          convert(mv, ctx, frame, scope, fields, call.arguments().getFirst(), "D");
          mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "abs", "(D)D", false);
        } else {
          convert(mv, ctx, frame, scope, fields, call.arguments().getFirst(), "I");
          mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "abs", "(I)I", false);
        }
      }
      case "min", "max" -> {
        requireArity(call, callee, 2);
        boolean f = isFloatArg(mv, ctx, frame, scope, fields, call) || isFloatArgAt(mv, ctx, frame, scope, fields, call, 1);
        String desc = f ? "(DD)D" : "(II)I";
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().get(0));
        convert(mv, ctx, frame, scope, fields, call.arguments().get(0), f ? "D" : "I");
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().get(1));
        convert(mv, ctx, frame, scope, fields, call.arguments().get(1), f ? "D" : "I");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, callee, desc, false);
      }
      case "sqrt", "round" -> {
        requireArity(call, callee, 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        convert(mv, ctx, frame, scope, fields, call.arguments().getFirst(), "D");
        String desc = "sqrt".equals(callee) ? "(D)D" : "(D)I";
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, callee, desc, false);
      }
      case "range" -> emitRangeCall(mv, ctx, frame, scope, fields, call);
      case "sum" -> {
        requireArity(call, "sum", 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "sum", "([I)I", false);
      }
      case "ord" -> {
        requireArity(call, "ord", 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        boxIfNeeded(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "ord", "(Ljava/lang/String;)I", false);
      }
      case "chr" -> {
        requireArity(call, "chr", 1);
        emitExpr(mv, ctx, frame, scope, fields, call.arguments().getFirst());
        convert(mv, ctx, frame, scope, fields, call.arguments().getFirst(), "I");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, "chr", "(I)Ljava/lang/String;", false);
      }
      default -> throw new UnsupportedOperationException("Unknown callable: " + callee);
    }
  }

  private void emitArgs(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, CallExpr call, FuncDef method, boolean skipSelf) {
    List<Param> params = method == null ? List.of() : method.parameters();
    int start = (method != null && skipSelf) ? 1 : 0;
    int varargIndex = -1;
    for (int i = start; i < params.size(); i++) {
      if (params.get(i).isVararg()) {
        varargIndex = i;
        break;
      }
    }
    int namedCount = varargIndex < 0 ? params.size() - start : varargIndex - start;
    List<Expr> positional = call.arguments();
    for (int i = start; i < params.size(); i++) {
      Param param = params.get(i);
      int positionalIndex = i - start;
      if (param.isVararg()) {
        int count = positional.size() - namedCount /* start */;
        mv.visitLdcInsn(count);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        for (int j = namedCount /* start */; j < positional.size(); j++) {
          mv.visitInsn(Opcodes.DUP);
          emitIntConstant(mv, j - namedCount /* start */);
          emitExpr(mv, ctx, frame, scope, fields, positional.get(j));
          boxIfNeeded(mv, ctx, frame, scope, fields, positional.get(j));
          mv.visitInsn(Opcodes.AASTORE);
        }
        continue;
      }
      Expr value;
      if (positionalIndex < positional.size()) {
        value = positional.get(positionalIndex);
      } else {
        value = namedArgValue(call, param.name());
        if (value == null) {
          value = param.default_();
          if (value == null) {
            throw new UnsupportedOperationException("Missing argument for parameter '" + param.name() + "'");
          }
        }
      }
      emitExpr(mv, ctx, frame, scope, fields, value);
      convert(mv, ctx, frame, scope, fields, value, paramDesc(param));
    }
  }

  private Expr namedArgValue(CallExpr call, String name) {
    for (NamedArg kw : call.keywords()) {
      if (kw.name().equals(name)) {
        return kw.value();
      }
    }
    return null;
  }

  private void emitVarargsCall(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, CallExpr call, String methodName, String desc, String[] optionNames, String[] optionTypes, Expr[] optionDefaults) {
    // Pack positional arguments into an Object[].
    List<Expr> pos = call.arguments();
    mv.visitLdcInsn(pos.size());
    mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
    for (int i = 0; i < pos.size(); i++) {
      mv.visitInsn(Opcodes.DUP);
      emitIntConstant(mv, i);
      emitExpr(mv, ctx, frame, scope, fields, pos.get(i));
      boxIfNeeded(mv, ctx, frame, scope, fields, pos.get(i));
      mv.visitInsn(Opcodes.AASTORE);
    }
    // Forward each option: keyword override if given, else the default (null default -> null).
    for (int i = 0; i < optionNames.length; i++) {
      Expr kw = optionDefault(call, optionNames[i], null);
      if (kw != null) {
        emitExpr(mv, ctx, frame, scope, fields, kw);
        convert(mv, ctx, frame, scope, fields, kw, optionTypes[i]);
      } else if (optionDefaults[i] == null) {
        mv.visitInsn(Opcodes.ACONST_NULL);
      } else {
        emitExpr(mv, ctx, frame, scope, fields, optionDefaults[i]);
        convert(mv, ctx, frame, scope, fields, optionDefaults[i], optionTypes[i]);
      }
    }
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, RuntimeBuiltins.CLASS_INTERNAL, methodName, desc, false);
  }

  private Expr optionDefault(CallExpr call, String name, Expr default_) {
    for (NamedArg kw : call.keywords()) {
      if (kw.name().equals(name)) {
        return kw.value();
      }
    }
    return default_;
  }

  private void requireArity(CallExpr call, String name, int expected) {
    if (call.arguments().size() != expected) {
      throw new UnsupportedOperationException(name + " expects " + expected + " argument(s)");
    }
  }

  private boolean isFloatArg(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, CallExpr call) {
    return isFloatArgAt(mv, ctx, frame, scope, fields, call, 0);
  }

  private boolean isFloatArgAt(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, CallExpr call, int index) {
    String t = typeOf(mv, ctx, frame, scope, fields, call.arguments().get(index));
    return "D".equals(t);
  }

  private String descriptorFor(String type) {
    return switch (type) {
      case "int" -> "I";
      case "float" -> "D";
      case "str" -> "Ljava/lang/String;";
      case "bool" -> "Z";
      default -> "L" + internalName(type) + ";";
    };
  }

  private String literalDesc(Expr expr) {
    if (expr instanceof IntLiteral) {
      return "I";
    }
    if (expr instanceof FloatLiteral) {
      return "D";
    }
    if (expr instanceof StringLiteral) {
      return "Ljava/lang/String;";
    }
    if (expr instanceof BoolLiteral) {
      return "Z";
    }
    return null;
  }

  private String paramDesc(Param param) {
    if (param.isVararg()) {
      return "[Ljava/lang/Object;";
    }
    return param.type().equals("obj") ? OBJECT : descriptorFor(param.type());
  }

  private String paramDescs(FuncDef func, boolean skipSelf) {
    StringBuilder sb = new StringBuilder();
    int start = skipSelf ? 1 : 0;
    for (int i = start; i < func.parameters().size(); i++) {
      sb.append(paramDesc(func.parameters().get(i)));
    }
    return sb.toString();
  }

  private String typeOf(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Expr expr) {
    if (expr instanceof IntLiteral) {
      return "I";
    }
    if (expr instanceof FloatLiteral) {
      return "D";
    }
    if (expr instanceof StringLiteral) {
      return "Ljava/lang/String;";
    }
    if (expr instanceof BoolLiteral) {
      return "Z";
    }
    if (expr instanceof Identifier(String name)) {
      LocalVar slot = frame.locals.get(name);
      if (slot != null) {
        return slot.desc;
      }
      if (classNames.contains(name)) {
        return "class:" + name;
      }
      throw new UnsupportedOperationException("Undeclared identifier: " + name);
    }
    if (expr instanceof GetAttr(Expr receiver, String name)) {
      String receiverType = typeOf(mv, ctx, frame, scope, fields, receiver);
      return scope.fields.get(classOf(receiverType)).getOrDefault(name, OBJECT);
    }
    if (expr instanceof BinExpr(BinExpr.Op op, Expr left, Expr right)) {
      if (isBooleanResult(op)) {
        return "Z";
      }
      String leftType = typeOf(mv, ctx, frame, scope, fields, left);
      String rightType = typeOf(mv, ctx, frame, scope, fields, right);
      if (op == BinExpr.Op.ADD && (isStringType(leftType) || isStringType(rightType))) {
        return STRING_TYPE;
      }
      boolean floatOp = "D".equals(leftType) || "D".equals(rightType);
      return floatOp ? "D" : "I";
    }
    if (expr instanceof UnaryExpr(UnaryExpr.Op op, Expr operand)) {
      return op == UnaryExpr.Op.NOT ? "Z" : typeOf(mv, ctx, frame, scope, fields, operand);
    }
    if (expr instanceof CallExpr call) {
      if (call.callee() instanceof Identifier(String callee)) {
        if (classNames.contains(callee)) {
          return "L" + internalName(callee) + ";";
        }
        return switch (callee) {
          case "len", "int", "round", "ord", "sum" -> "I";
          case "float", "sqrt" -> "D";
          case "str", "input", "chr" -> "Ljava/lang/String;";
          case "bool" -> "Z";
          case "range" -> "[I";
          case "abs", "min", "max" -> isFloatArg(mv, ctx, frame, scope, fields, call) ? "D" : "I";
          default -> OBJECT;
        };
      }
      return OBJECT;
    }
    return OBJECT;
  }

  private boolean isBooleanResult(BinExpr.Op op) {
    return op == BinExpr.Op.EQ || op == BinExpr.Op.NEQ || op == BinExpr.Op.LT || op == BinExpr.Op.GT || op == BinExpr.Op.LTE ||
        op == BinExpr.Op.GTE || op == BinExpr.Op.AND || op == BinExpr.Op.OR;
  }

  private String classOf(String type) {
    if (type != null && type.startsWith("class:")) {
      return type.substring("class:".length());
    }
    if (type != null && type.startsWith("L" + CLASS_PREFIX) && type.endsWith(";")) {
      return type.substring(CLASS_PREFIX.length() + 1, type.length() - 1);
    }
    throw new UnsupportedOperationException("Not an object type: " + type);
  }

  private String internalName(String className) {
    return CLASS_PREFIX + className;
  }

  private boolean isObjectType(String type) {
    return type != null && (type.startsWith("L") || type.startsWith("["));
  }

  private boolean isStringType(String type) {
    return STRING_TYPE.equals(type);
  }

  private boolean isPrimitiveNumeric(String type) {
    return type.equals("I") || type.equals("J") || type.equals("F") || type.equals("D");
  }

  private boolean isInstanceMethod(FuncDef func) {
    return !func.parameters().isEmpty() && func.parameters().getFirst().name().equals("self");
  }

  private FuncDef findMethod(ClassDef clazz, String name) {
    if (clazz == null) {
      return null;
    }
    for (FuncDef method : clazz.methods()) {
      if (method.name().equals(name)) {
        return method;
      }
    }
    return null;
  }

  private void boxIfNeeded(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Expr expr) {
    boxPrimitive(mv, typeOf(mv, ctx, frame, scope, fields, expr));
  }

  private void boxPrimitive(MethodVisitor mv, String desc) {
    switch (desc) {
      case "I" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
      case "J" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
      case "F" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
      case "D" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
      case "Z" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
      default -> {
        // already an object reference
      }
    }
  }

  private void convert(MethodVisitor mv, MethodCtx ctx, MethodFrame frame, ProgramScope scope, Map<String, String> fields, Expr expr, String target) {
    String from = typeOf(mv, ctx, frame, scope, fields, expr);
    if (from.equals(target)) {
      return;
    }
    if ("I".equals(from) && "D".equals(target)) {
      mv.visitInsn(Opcodes.I2D);
      return;
    }
    if (target.equals(OBJECT)) {
      boxPrimitive(mv, from);
      return;
    }
    throw new UnsupportedOperationException("Cannot convert " + from + " to " + target);
  }

  private void load(MethodVisitor mv, LocalVar var) {
    switch (var.desc) {
      case "D" -> mv.visitVarInsn(Opcodes.DLOAD, var.slot);
      case "I", "Z" -> mv.visitVarInsn(Opcodes.ILOAD, var.slot);
      default -> mv.visitVarInsn(Opcodes.ALOAD, var.slot);
    }
  }

  private void store(MethodVisitor mv, LocalVar var) {
    switch (var.desc) {
      case "D" -> mv.visitVarInsn(Opcodes.DSTORE, var.slot);
      case "I", "Z" -> mv.visitVarInsn(Opcodes.ISTORE, var.slot);
      default -> mv.visitVarInsn(Opcodes.ASTORE, var.slot);
    }
  }

  private LocalVar allocateLocal(MethodFrame frame, String name, String desc) {
    LocalVar var = new LocalVar(frame.nextSlot, desc);
    frame.locals.put(name, var);
    frame.nextSlot += width(desc);
    return var;
  }

  private int width(String desc) {
    return "D".equals(desc) || "J".equals(desc) ? 2 : 1;
  }

  private record MethodCtx(String className, boolean isConstructor, boolean isVoidMain) {
  }

  private record LocalVar(int slot, String desc) {
  }

  private static class MethodFrame {
    final Map<String, LocalVar> locals = new HashMap<>();
    int nextSlot;
  }

  private static class ProgramScope {
    final Map<String, ClassDef> classes = new HashMap<>();
    final Map<String, Map<String, String>> fields = new HashMap<>();
  }
}
