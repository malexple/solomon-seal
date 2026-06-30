package ru.mcs.llmrobot.core.tools;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Полнотекстовый поиск по коду через FTS5 SQLite.
 */
public class SearchCodeTool implements AgentTool {

    @Override public String name() { return "search_code"; }
    @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
    @Override public boolean requiresConfirmation() { return false; }

    @Override
    public String description() {
        return "Full-text search across all indexed project files. Returns file paths containing the query.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "query":   {"type": "string",  "description": "Search query (FTS5 syntax supported)"},
                "project": {"type": "string",  "description": "Project alias (optional, searches all)"},
                "limit":   {"type": "integer", "description": "Max results (default 20)"}
              },
              "required": ["query"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx) {
        var query   = (String) args.get("query");
        var project = (String) args.get("project");
        int limit   = args.containsKey("limit")
                ? ((Number) args.get("limit")).intValue() : 20;

        if (query == null)
            return CompletableFuture.completedFuture(ToolResult.fail("query is required"));

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
                    var paths = index.searchContent(query, limit);
                    for (var path : paths) {
                        sb.append(path).append("\n");
                        total++;
                    }
                } catch (SQLException e) {
                    sb.append("Error: ").append(e.getMessage()).append("\n");
                }
            }
            return total == 0
                    ? ToolResult.fail("No files found matching: " + query)
                    : ToolResult.ok(sb.toString().trim());
        });
    }
}