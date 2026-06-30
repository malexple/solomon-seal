package ru.mcs.llmrobot.core.tools;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Ищет символы (классы, методы, поля) по SQLite индексу.
 */
public class FindSymbolTool implements AgentTool {

    @Override public String name() { return "find_symbol"; }
    @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
    @Override public boolean requiresConfirmation() { return false; }

    @Override
    public String description() {
        return "Find classes, methods or fields by name in the project index. Supports partial match.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "name":    {"type": "string", "description": "Symbol name (partial match supported)"},
                "project": {"type": "string", "description": "Project alias to search in (optional, searches all if omitted)"},
                "kind":    {"type": "string", "description": "Filter by kind: CLASS, INTERFACE, METHOD, FIELD, ENUM, RECORD, CONSTRUCTOR, CONSTANT"}
              },
              "required": ["name"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx) {
        var name    = (String) args.get("name");
        var project = (String) args.get("project");
        var kind    = (String) args.get("kind");

        if (name == null)
            return CompletableFuture.completedFuture(ToolResult.fail("name is required"));

        return CompletableFuture.supplyAsync(() -> {
            var sb = new StringBuilder();
            var projects = project != null
                    ? ctx.projects.stream().filter(p -> p.alias.equals(project)).toList()
                    : ctx.projects;

            if (projects.isEmpty())
                return ToolResult.fail("No indexed projects found");

            int total = 0;
            for (var proj : projects) {
                try {
                    var index = ctx.indexManager.getIndex(proj);
                    var symbols = index.findSymbols(name);
                    for (var s : symbols) {
                        if (kind != null && !s.kind.name().equalsIgnoreCase(kind)) continue;
                        sb.append(s.kind).append("  ")
                                .append(s.signature != null ? s.signature : s.name)
                                .append("  [").append(s.parentName != null ? s.parentName + " " : "")
                                .append("L").append(s.lineStart).append("]\n");
                        total++;
                    }
                } catch (SQLException e) {
                    sb.append("Error searching ").append(proj.displayName())
                            .append(": ").append(e.getMessage()).append("\n");
                }
            }
            return total == 0
                    ? ToolResult.fail("No symbols found matching: " + name)
                    : ToolResult.ok(sb.toString().trim());
        });
    }
}