package xyz.spigotrce.gyalang;

public record Token(Type type, String value, String filename, int position) {

    public enum Type {
        IDENT,
        INT,
        FLOAT,
        STRING,
        KEYWORD,
        NEWLINE,
        EOF
    }

    @Override
    public String toString() {
        return type + "('" + value + "')";
    }
}
