package xyz.spigotrce.gyalang;

import xyz.spigotrce.gyalang.ast.Program;

public class CodeGenerator {
    private final String filename;

    public CodeGenerator(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] generate(Program program) {
        // TODO: implement bytecode generation using ASM
        return new byte[0];
    }
}
