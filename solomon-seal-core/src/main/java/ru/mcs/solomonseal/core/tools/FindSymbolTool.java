package ru.mcs.solomonseal.core.tools;
import ru.mcs.solomonseal.spi.ToolPermission;
import ru.mcs.solomonseal.spi.ToolResult;

import ru.mcs.solomonseal.spi.AgentTool;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class FindSymbolTool implements AgentTool {

    @Override public String name() { return "find_symbol"; }
    @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
    @Override public boolean requiresConfirmation() { return false; }

    @Override
    public String description() {
        return "Find classes, methods, and fields by name in the indexed project SQLite database.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "query":   {"type": "string",  "description": "Symbol name or partial name"},
                "kind":    {"type": "string",  "description": "Filter: CLASS, METHOD, FIELD (optional)"},
                "project": {"type": "string",  "description": "Project alias (optional, searches all if omitted)"},
                "limit":   {"type": "integer", "description": "Max results (default 20)"}
              },
              "required": ["query"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ru.mcs.solomonseal.spi.ExecutionContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            var query = (String) args.get("query");
            if (query == null || query.isBlank()) {
                query = "";
            }
            String kindFilter    = (String) args.get("kind");
            String projectFilter = (String) args.get("project");
            int    limit         = args.containsKey("limit")
                    ? ((Number) args.get("limit")).intValue() : 20;

            var sb    = new StringBuilder();
            int total = 0;

            for (var project : ((ExecutionContext) ctx).projects) {
                if (projectFilter != null && !project.alias.equals(projectFilter)) continue;
                if (!project.indexed) continue;

                try {
                    var index   = ((ExecutionContext) ctx).indexManager.getIndex(project);
                    var symbols = index.findSymbols(query);

                    for (var s : symbols) {
                        if (total >= limit) break;
                        // kind — enum, сравниваем через name()
                        if (kindFilter != null && !kindFilter.equalsIgnoreCase(s.kind.name())) continue;

                        sb.append("[").append(project.alias).append("] ")
                                .append(s.kind.name()).append("  ")
                                .append(s.signature != null ? s.signature : s.name);
                        if (s.parentName != null)
                            sb.append("  (in ").append(s.parentName).append(")");
                        sb.append("  line:").append(s.lineStart).append("\n");
                        total++;
                    }
                } catch (Exception e) {
                    sb.append("[").append(project.alias).append("] error: ")
                            .append(e.getMessage()).append("\n");
                }
            }

            if (sb.isEmpty()) return ToolResult.fail("No symbols found for: " + query);
            return ToolResult.ok(sb.toString().trim());
        });
    }
}