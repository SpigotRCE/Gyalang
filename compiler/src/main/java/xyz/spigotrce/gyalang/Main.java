package xyz.spigotrce.gyalang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Main {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Usage: gylang <source.glg> [output-dir]");
      return;
    }

    Path sourcePath = Path.of(args[0]);
    if (!Files.exists(sourcePath)) {
      System.err.println("Error: file not found: " + sourcePath);
      return;
    }

    String source;
    try {
      source = Files.readString(sourcePath);
    } catch (IOException e) {
      System.err.println("Error reading file: " + e.getMessage());
      return;
    }

    Path outputDir = args.length > 1 ? Path.of(args[1]) : Path.of("out");

    Compiler compiler = new Compiler(source, sourcePath.getFileName().toString());
    try {
      Map<String, byte[]> classes = compiler.compile();
      for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
        Path classFile = outputDir.resolve(entry.getKey() + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, entry.getValue());
        System.out.println("Wrote " + classFile);
      }
      System.out.println("Run: java -cp " + outputDir + " gyalang.generated.Main");
    } catch (IOException e) {
      System.err.println("Error writing output: " + e.getMessage());
    }
  }
}
