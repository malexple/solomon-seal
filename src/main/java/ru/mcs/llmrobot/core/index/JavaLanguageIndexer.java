package ru.mcs.llmrobot.core.index;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Индексирует Java файлы через JavaParser.
 * Извлекает классы, интерфейсы, enum, методы, поля.
 */
public class JavaLanguageIndexer implements LanguageIndexer {

    private static final Logger log = LoggerFactory.getLogger(JavaLanguageIndexer.class);

    @Override
    public String language() {
        return "java";
    }

    @Override
    public List<String> fileExtensions() {
        return List.of(".java");
    }

    @Override
    public List<SymbolRecord> index(Path file) throws Exception {
        var symbols = new ArrayList<SymbolRecord>();
        var cu = StaticJavaParser.parse(file);

        // Классы, интерфейсы, enum, record
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            var kind = cls.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS;
            var begin = cls.getBegin().map(p -> p.line).orElse(0);
            var end   = cls.getEnd().map(p -> p.line).orElse(0);
            symbols.add(new SymbolRecord(
                    cls.getNameAsString(), kind,
                    buildClassSignature(cls), null,
                    begin, end
            ));

            // Методы класса
            cls.getMethods().forEach(method -> {
                var mBegin = method.getBegin().map(p -> p.line).orElse(0);
                var mEnd   = method.getEnd().map(p -> p.line).orElse(0);
                symbols.add(new SymbolRecord(
                        method.getNameAsString(), SymbolKind.METHOD,
                        buildMethodSignature(method),
                        cls.getNameAsString(),
                        mBegin, mEnd
                ));
            });

            // Конструкторы
            cls.getConstructors().forEach(ctor -> {
                var cBegin = ctor.getBegin().map(p -> p.line).orElse(0);
                var cEnd   = ctor.getEnd().map(p -> p.line).orElse(0);
                symbols.add(new SymbolRecord(
                        ctor.getNameAsString(), SymbolKind.CONSTRUCTOR,
                        buildConstructorSignature(ctor),
                        cls.getNameAsString(),
                        cBegin, cEnd
                ));
            });

            // Поля
            cls.getFields().forEach(field -> {
                field.getVariables().forEach(var -> {
                    var fBegin = field.getBegin().map(p -> p.line).orElse(0);
                    var fEnd   = field.getEnd().map(p -> p.line).orElse(0);
                    var isConst = field.isStatic() && field.isFinal();
                    symbols.add(new SymbolRecord(
                            var.getNameAsString(),
                            isConst ? SymbolKind.CONSTANT : SymbolKind.FIELD,
                            field.getElementType() + " " + var.getNameAsString(),
                            cls.getNameAsString(),
                            fBegin, fEnd
                    ));
                });
            });
        });

        // Enum
        cu.findAll(EnumDeclaration.class).forEach(en -> {
            var begin = en.getBegin().map(p -> p.line).orElse(0);
            var end   = en.getEnd().map(p -> p.line).orElse(0);
            symbols.add(new SymbolRecord(
                    en.getNameAsString(), SymbolKind.ENUM,
                    "enum " + en.getNameAsString(), null,
                    begin, end
            ));
        });

        // Record
        cu.findAll(RecordDeclaration.class).forEach(rec -> {
            var begin = rec.getBegin().map(p -> p.line).orElse(0);
            var end   = rec.getEnd().map(p -> p.line).orElse(0);
            symbols.add(new SymbolRecord(
                    rec.getNameAsString(), SymbolKind.RECORD,
                    "record " + rec.getNameAsString(), null,
                    begin, end
            ));
        });

        return symbols;
    }

    private String buildClassSignature(ClassOrInterfaceDeclaration cls) {
        var sb = new StringBuilder();
        cls.getModifiers().forEach(m -> sb.append(m.toString().toLowerCase().trim()).append(" "));
        sb.append(cls.isInterface() ? "interface " : "class ");
        sb.append(cls.getNameAsString());
        if (!cls.getExtendedTypes().isEmpty())
            sb.append(" extends ").append(cls.getExtendedTypes().get(0).getNameAsString());
        if (!cls.getImplementedTypes().isEmpty()) {
            sb.append(" implements ");
            cls.getImplementedTypes().forEach(t -> sb.append(t.getNameAsString()).append(", "));
        }
        return sb.toString().trim().replaceAll(", $", "");
    }

    private String buildMethodSignature(MethodDeclaration method) {
        var sb = new StringBuilder();
        method.getModifiers().forEach(m -> sb.append(m.toString().toLowerCase().trim()).append(" "));
        sb.append(method.getType()).append(" ");
        sb.append(method.getNameAsString()).append("(");
        var params = method.getParameters().stream()
                .map(p -> p.getType() + " " + p.getNameAsString())
                .toList();
        sb.append(String.join(", ", params)).append(")");
        return sb.toString().trim();
    }

    private String buildConstructorSignature(ConstructorDeclaration ctor) {
        var sb = new StringBuilder();
        ctor.getModifiers().forEach(m -> sb.append(m.toString().toLowerCase().trim()).append(" "));
        sb.append(ctor.getNameAsString()).append("(");
        var params = ctor.getParameters().stream()
                .map(p -> p.getType() + " " + p.getNameAsString())
                .toList();
        sb.append(String.join(", ", params)).append(")");
        return sb.toString().trim();
    }
}