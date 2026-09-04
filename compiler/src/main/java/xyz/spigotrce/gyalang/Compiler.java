package xyz.spigotrce.gyalang;

import xyz.spigotrce.gyalang.ast.Program;

public class Compiler {
    private final String source;
    private final String filename;

    public Compiler(String source, String filename) {
        this.source = source;
        this.filename = filename;
    }

    public String getSource() {
        return source;
    }

    public String getFilename() {
        return filename;
    }

    public Program parse() {
        Lexer lexer = new Lexer(source, filename);
        var tokens = lexer.tokenize();
        Parser parser = new Parser(tokens, filename);
        return parser.parse();
    }

    public byte[] compile() {
        Program program = parse();
        CodeGenerator codegen = new CodeGenerator(filename);
        return codegen.generate(program);
    }
}
