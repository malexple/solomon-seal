package ru.mcs.solomonseal.core.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ListTreeTool implements AgentTool {

    @Override public String name() { return "list_tree"; }
    @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
    @Override public boolean requiresConfirmation() { return false; }

    @Override
    public String description() {
        return "List directory tree up to given depth.";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "dir":   {"type": "string",  "description": "Directory path"},
                "depth": {"type": "integer", "description": "Max depth (default 3)"}
              },
              "required": ["dir"]
            }""";
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> args, ExecutionContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            var dirStr = (String) args.get("dir");
            if ((dirStr == null || dirStr.isBlank()) && !ctx.projects.isEmpty()) {
                dirStr = ctx.projects.get(0).rootPath;
            }
            if (dirStr == null || dirStr.isBlank())
                return ToolResult.fail("dir is required and no projects are attached to session");

            int depth = args.containsKey("depth")
                    ? ((Number) args.get("depth")).intValue() : 3;
            var sb = new StringBuilder();
            try {
                appendTree(sb, ctx.resolvePath(dirStr), "", 0, depth);
                return ToolResult.ok(sb.toString());
            } catch (IOException e) {
                return ToolResult.fail("ListTree error: " + e.getMessage());
            }
        });
    }

    private void appendTree(StringBuilder sb, java.nio.file.Path dir,
                            String prefix, int depth, int maxDepth) throws IOException {
        if (depth >= maxDepth) return;
        try (var stream = Files.list(dir)) {
            var entries = stream
                    .filter(p -> {
                        var n = p.getFileName().toString();
                        return !n.startsWith(".") && !n.equals("build")
                                && !n.equals("target") && !n.equals("node_modules");
                    })
                    .sorted().toList();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                var last  = (i == entries.size() - 1);
                sb.append(prefix).append(last ? "└── " : "├── ")
                        .append(entry.getFileName()).append("\n");
                if (Files.isDirectory(entry))
                    appendTree(sb, entry, prefix + (last ? "    " : "│   "), depth + 1, maxDepth);
            }
        }
    }
}