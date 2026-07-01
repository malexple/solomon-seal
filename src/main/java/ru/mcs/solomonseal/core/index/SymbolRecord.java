package ru.mcs.solomonseal.core.index;

/**
 * Символ из AST: класс, метод, поле и т.д.
 */
public class SymbolRecord {
    public long     id;
    public long     fileId;
    public String   name;
    public SymbolKind kind;
    public String   signature;   // полная сигнатура: "public String getName()"
    public String   parentName;  // имя родительского класса (для методов/полей)
    public int      lineStart;
    public int      lineEnd;

    public SymbolRecord() {}

    public SymbolRecord(String name, SymbolKind kind, String signature,
                        String parentName, int lineStart, int lineEnd) {
        this.name       = name;
        this.kind       = kind;
        this.signature  = signature;
        this.parentName = parentName;
        this.lineStart  = lineStart;
        this.lineEnd    = lineEnd;
    }
}