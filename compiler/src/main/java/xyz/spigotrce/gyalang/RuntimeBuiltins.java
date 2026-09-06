package xyz.spigotrce.gyalang;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generates the runtime class that provides the built-in functions available to Gylang programs
 * (print, input, len, int, float, str, bool).
 *
 * <p>The produced class contains one static method per built-in. Compiled Gylang programs invoke
 * these methods directly. Frames are computed by ASM's {@code COMPUTE_FRAMES}.
 */
class RuntimeBuiltins {
  static final String CLASS_INTERNAL = "xyz/spigotrce/gyalang/runtime/Builtins";

  private RuntimeBuiltins() {}

  static byte[] generate() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
        CLASS_INTERNAL,
        null,
        "java/lang/Object",
        null);

    emitPrint(cw);
    emitInput(cw);
    emitLen(cw);
    emitInt(cw);
    emitFloat(cw);
    emitStr(cw);
    emitBool(cw);
    emitAbs(cw);
    emitMin(cw);
    emitMax(cw);
    emitRound(cw);
    emitSqrt(cw);
    emitRange(cw);
    emitSum(cw);
    emitOrd(cw);
    emitChr(cw);

    cw.visitEnd();
    return cw.toByteArray();
  }

  private static void emitPrint(ClassWriter cw) {
    // Object print(Object[] objects, String sep, String end, Object file, boolean flush)
    //   System.out.print(stringify(args, sep)); System.out.print(end); return null;
    // file/flush are accepted for Python signature compatibility and ignored
    // (Gylang always writes to stdout).
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "print",
            "([Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Z)Ljava/lang/Object;",
            null,
            null);
    mv.visitCode();

    // StringBuilder sb = new StringBuilder();
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
    mv.visitVarInsn(Opcodes.ASTORE, 5); // sb

    // for (int i = 0; i < args.length; i++)
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitVarInsn(Opcodes.ISTORE, 6); // i
    Label loop = new Label();
    Label endFor = new Label();
    mv.visitLabel(loop);
    mv.visitVarInsn(Opcodes.ILOAD, 6);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.ARRAYLENGTH);
    mv.visitJumpInsn(Opcodes.IF_ICMPGE, endFor);

    // if (i > 0) sb.append(sep);
    Label notFirst = new Label();
    mv.visitVarInsn(Opcodes.ILOAD, 6);
    mv.visitJumpInsn(Opcodes.IFLE, notFirst);
    mv.visitVarInsn(Opcodes.ALOAD, 5);
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/lang/StringBuilder",
        "append",
        "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
        false);
    mv.visitInsn(Opcodes.POP);
    mv.visitLabel(notFirst);

    // sb.append(String.valueOf(args[i]));
    mv.visitVarInsn(Opcodes.ALOAD, 5);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitVarInsn(Opcodes.ILOAD, 6);
    mv.visitInsn(Opcodes.AALOAD);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/String",
        "valueOf",
        "(Ljava/lang/Object;)Ljava/lang/String;",
        false);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/lang/StringBuilder",
        "append",
        "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
        false);
    mv.visitInsn(Opcodes.POP);

    // i++
    mv.visitVarInsn(Opcodes.ILOAD, 6);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IADD);
    mv.visitVarInsn(Opcodes.ISTORE, 6);
    mv.visitJumpInsn(Opcodes.GOTO, loop);
    mv.visitLabel(endFor);

    // System.out.print(sb.toString()); System.out.print(end);
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitVarInsn(Opcodes.ALOAD, 5);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/lang/StringBuilder",
        "toString",
        "()Ljava/lang/String;",
        false);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitVarInsn(Opcodes.ALOAD, 2);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);

    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitInput(ClassWriter cw) {
    // String input() throws IOException, BufferedReader.readLine()
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "input",
            "(Ljava/lang/String;)Ljava/lang/String;",
            null,
            new String[] {"java/io/IOException"});
    mv.visitCode();

    // prompt: if non-null, print it first
    Label noPrompt = new Label();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitJumpInsn(Opcodes.IFNULL, noPrompt);
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
    mv.visitLabel(noPrompt);

    // new BufferedReader(new InputStreamReader(System.in))
    mv.visitTypeInsn(Opcodes.NEW, "java/io/BufferedReader");
    mv.visitInsn(Opcodes.DUP);
    mv.visitTypeInsn(Opcodes.NEW, "java/io/InputStreamReader");
    mv.visitInsn(Opcodes.DUP);
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "java/io/InputStreamReader",
        "<init>",
        "(Ljava/io/InputStream;)V",
        false);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/BufferedReader", "readLine", "()Ljava/lang/String;", false);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitLen(ClassWriter cw) {
    // int len(String s) { return s.length(); }
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "len", "(Ljava/lang/String;)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitInt(ClassWriter cw) {
    // int int(Object value) { return Integer.parseInt(String.valueOf(value)); }
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "int", "(Ljava/lang/Object;)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/String",
        "valueOf",
        "(Ljava/lang/Object;)Ljava/lang/String;",
        false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitFloat(ClassWriter cw) {
    // double float(Object value) { return Double.parseDouble(String.valueOf(value)); }
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "float", "(Ljava/lang/Object;)D", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/String",
        "valueOf",
        "(Ljava/lang/Object;)Ljava/lang/String;",
        false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
    mv.visitInsn(Opcodes.DRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitStr(ClassWriter cw) {
    // String str(Object value) { return String.valueOf(value); }
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "str",
            "(Ljava/lang/Object;)Ljava/lang/String;",
            null,
            null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/String",
        "valueOf",
        "(Ljava/lang/Object;)Ljava/lang/String;",
        false);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitBool(ClassWriter cw) {
    // boolean bool(Object value) { return Boolean.parseBoolean(String.valueOf(value)); }
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bool", "(Ljava/lang/Object;)Z", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/String",
        "valueOf",
        "(Ljava/lang/Object;)Ljava/lang/String;",
        false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitAbs(ClassWriter cw) {
    // int abs(int v) { return Math.abs(v); }
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "abs", "(I)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ILOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // double abs(double v) { return Math.abs(v); }
    mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "abs", "(D)D", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.DLOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false);
    mv.visitInsn(Opcodes.DRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitMin(ClassWriter cw) {
    // int min(int a, int b) { return Math.min(a, b); }
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "min", "(II)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ILOAD, 0);
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I", false);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // double min(double a, double b) { return Math.min(a, b); }
    mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "min", "(DD)D", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.DLOAD, 0);
    mv.visitVarInsn(Opcodes.DLOAD, 2);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(DD)D", false);
    mv.visitInsn(Opcodes.DRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitMax(ClassWriter cw) {
    // int max(int a, int b) { return Math.max(a, b); }
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "max", "(II)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ILOAD, 0);
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // double max(double a, double b) { return Math.max(a, b); }
    mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "max", "(DD)D", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.DLOAD, 0);
    mv.visitVarInsn(Opcodes.DLOAD, 2);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(DD)D", false);
    mv.visitInsn(Opcodes.DRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitRound(ClassWriter cw) {
    // int round(double v) { return (int) Math.round(v); }
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "round", "(D)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.DLOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(D)J", false);
    mv.visitInsn(Opcodes.L2I);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitSqrt(ClassWriter cw) {
    // double sqrt(double v) { return Math.sqrt(v); }
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sqrt", "(D)D", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.DLOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false);
    mv.visitInsn(Opcodes.DRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitRange(ClassWriter cw) {
    // int[] range(int start, int end, int step)
    // Computes count based on step direction, skips if count <= 0.
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "range", "(III)[I", null, null);
    mv.visitCode();

    Label stepPositive = new Label();
    Label countDone = new Label();
    Label zeroCount = new Label();
    Label fill = new Label();

    // if (step > 0) count = (end - start + step - 1) / step; else count = (start - end - step - 1)
    // / -step
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitJumpInsn(Opcodes.IFGT, stepPositive);

    // negative step: count = (start - end - step - 1) / (-step)
    mv.visitVarInsn(Opcodes.ILOAD, 0);
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitInsn(Opcodes.ISUB);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.ISUB);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.ISUB);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.INEG);
    mv.visitInsn(Opcodes.IDIV);
    mv.visitJumpInsn(Opcodes.GOTO, countDone);

    mv.visitLabel(stepPositive);
    // positive step: count = (end - start + step - 1) / step
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitVarInsn(Opcodes.ILOAD, 0);
    mv.visitInsn(Opcodes.ISUB);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.IADD);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.ISUB);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.IDIV);

    mv.visitLabel(countDone);
    // count <= 0 -> return empty array
    mv.visitInsn(Opcodes.DUP);
    mv.visitJumpInsn(Opcodes.IFLE, zeroCount);
    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
    mv.visitJumpInsn(Opcodes.GOTO, fill);
    mv.visitLabel(zeroCount);
    mv.visitInsn(Opcodes.POP);
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
    mv.visitInsn(Opcodes.ARETURN);

    // fill loop: start=start(done), arr != null
    // Note: after NEWARRAY, the count was consumed; we need arr on stack.
    // Simpler: re-derive values inside fill with a JVM loop using temps.
    mv.visitLabel(fill);
    mv.visitVarInsn(Opcodes.ASTORE, 3); // arr
    mv.visitVarInsn(Opcodes.ILOAD, 0); // current = start
    mv.visitVarInsn(Opcodes.ISTORE, 4);
    mv.visitInsn(Opcodes.ICONST_0); // i = 0
    mv.visitVarInsn(Opcodes.ISTORE, 5);

    Label loopStart = new Label();
    Label loopEnd = new Label();
    mv.visitLabel(loopStart);
    mv.visitVarInsn(Opcodes.ILOAD, 5);
    mv.visitVarInsn(Opcodes.ALOAD, 3);
    mv.visitInsn(Opcodes.ARRAYLENGTH);
    mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
    mv.visitVarInsn(Opcodes.ALOAD, 3);
    mv.visitVarInsn(Opcodes.ILOAD, 5);
    mv.visitVarInsn(Opcodes.ILOAD, 4);
    mv.visitInsn(Opcodes.IASTORE);
    mv.visitVarInsn(Opcodes.ILOAD, 4);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.IADD);
    mv.visitVarInsn(Opcodes.ISTORE, 4);
    mv.visitVarInsn(Opcodes.ILOAD, 5);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IADD);
    mv.visitVarInsn(Opcodes.ISTORE, 5);
    mv.visitJumpInsn(Opcodes.GOTO, loopStart);
    mv.visitLabel(loopEnd);
    mv.visitVarInsn(Opcodes.ALOAD, 3);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitSum(ClassWriter cw) {
    // int sum(int[] arr) { int s = 0; for (int v : arr) s += v; return s; }
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sum", "([I)I", null, null);
    mv.visitCode();
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitVarInsn(Opcodes.ISTORE, 1);
    Label loop = new Label();
    Label end = new Label();
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitVarInsn(Opcodes.ISTORE, 2);
    mv.visitLabel(loop);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.ARRAYLENGTH);
    mv.visitJumpInsn(Opcodes.IF_ICMPGE, end);
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.IALOAD);
    mv.visitInsn(Opcodes.IADD);
    mv.visitVarInsn(Opcodes.ISTORE, 1);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IADD);
    mv.visitVarInsn(Opcodes.ISTORE, 2);
    mv.visitJumpInsn(Opcodes.GOTO, loop);
    mv.visitLabel(end);
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitOrd(ClassWriter cw) {
    // int ord(String s) { return s.charAt(0); }
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "ord", "(Ljava/lang/String;)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void emitChr(ClassWriter cw) {
    // String chr(int v) { return String.valueOf((char) v); }
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "chr", "(I)Ljava/lang/String;", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ILOAD, 0);
    mv.visitInsn(Opcodes.I2C);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(C)Ljava/lang/String;", false);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }
}
