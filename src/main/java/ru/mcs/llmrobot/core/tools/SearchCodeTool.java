package ru.mcs.llmrobot.core.tools;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SearchCodeTool implements AgentTool {

    @Override public String name() { return "search_code"; }
    @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
    @Override public boolean requiresConfirmation() { return false; }

    @Override
    public String description() {
        return "Full-text search across indexed project files using SQLite FTS5. Returns file paths with snippets.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "query":   {"type": "string",  "description": "Search terms"},
                "project": {"type": "string",  "description": "Project alias (optional)"},
                "limit":   {"type": "integer", "description": "Max results (default 15)"}
              },
              "required": ["query"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            var query = (String) args.get("query");
            if (query == null || query.isBlank())
                return ToolResult.fail("query is required");

            String projectFilter = (String) args.get("project");
            int    limit         = args.containsKey("limit")
                    ? ((Number) args.get("limit")).intValue() : 15;

            var sb = new StringBuilder();

            for (var project : ctx.projects) {
                if (projectFilter != null && !project.alias.equals(projectFilter)) continue;
                if (!project.indexed) continue;

                try {
                    var index = ctx.indexManager.getIndex(project);
                    var paths = index.searchContent(query, limit);

                    if (paths.isEmpty()) continue;
                    sb.append("## ").append(project.alias).append("\n");
                    paths.forEach(p -> sb.append("  ").append(p).append("\n"));
                    sb.append("\n");
                } catch (Exception e) {
                    sb.append("[").append(project.alias).append("] error: ")
                            .append(e.getMessage()).append("\n");
                }
            }

            if (sb.isEmpty()) return ToolResult.fail("No results for: " + query);
            return ToolResult.ok(sb.toString().trim());
        });
    }
}